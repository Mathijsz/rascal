/*******************************************************************************
 * Copyright (c) 2009-2011 CWI
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   * Jurgen J. Vinju - Jurgen.Vinju@cwi.nl - CWI
 *   * Bas Basten - Bas.Basten@cwi.nl (CWI)
 *   * Jouke Stoel - Jouke.Stoel@cwi.nl (CWI)
 *   * Mark Hills - Mark.Hills@cwi.nl (CWI)
 *   * Arnold Lankamp - Arnold.Lankamp@cwi.nl
 *   * Anastasia Izmaylova - A.Izmaylova@cwi.nl - CWI
 *   * Davy Landman - Davy.Landman@cwi.nl (CWI)
 *******************************************************************************/
package org.rascalmpl.library.lang.java.m3.internal;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.function.BiConsumer;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.Comment;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FileASTRequestor;
import org.rascalmpl.interpreter.IEvaluatorContext;
import org.rascalmpl.interpreter.utils.RuntimeExceptionFactory;
import org.rascalmpl.library.experiments.Compiler.RVM.Interpreter.RascalRuntimeException;
import org.rascalmpl.parser.gtd.io.InputConverter;
import org.rascalmpl.unicode.UnicodeDetector;
import org.rascalmpl.uri.URIResolverRegistry;
import org.rascalmpl.value.IBool;
import org.rascalmpl.value.ISet;
import org.rascalmpl.value.ISetWriter;
import org.rascalmpl.value.ISourceLocation;
import org.rascalmpl.value.IString;
import org.rascalmpl.value.IValue;
import org.rascalmpl.value.IValueFactory;
import org.rascalmpl.value.type.TypeStore;

public class EclipseJavaCompiler {
    protected final IValueFactory VF;

    public EclipseJavaCompiler(IValueFactory vf) {
        this.VF = vf;
    }

    public IValue createM3FromJarClass(ISourceLocation jarLoc, IEvaluatorContext eval) {
        TypeStore store = new TypeStore();
        store.extendStore(eval.getHeap().getModule("lang::java::m3::Core").getStore());
        store.extendStore(eval.getHeap().getModule("lang::java::m3::AST").getStore());
        JarConverter converter = new JarConverter(store, new HashMap<>());
        converter.convert(jarLoc, eval);
        return converter.getModel(false);
    }

    public IValue createM3sFromFiles(ISet files, ISet sourcePath, ISet classPath, IString javaVersion, IEvaluatorContext eval) {
        try {
            TypeStore store = new TypeStore();
            store.extendStore(eval.getHeap().getModule("lang::java::m3::Core").getStore());
            store.extendStore(eval.getHeap().getModule("lang::java::m3::AST").getStore());

            Map<String, ISourceLocation> cache = new HashMap<>();
            ISetWriter result = VF.setWriter();
            for (IValue file: files) {
                ISourceLocation loc = (ISourceLocation) file;
                CompilationUnit cu = this.getCompilationUnit(loc.getPath(), getFileContents(loc, eval), true, javaVersion, translatePaths(sourcePath), translatePaths(classPath));

                result.insert(convertToM3(store, cache, loc, cu));
            }
            return result.done();
        } catch (IOException e) {
            throw RuntimeExceptionFactory.io(VF.string(e.getMessage()), null, null);
        }
    }

    protected IValue convertToM3(TypeStore store, Map<String, ISourceLocation> cache, ISourceLocation loc,
            CompilationUnit cu) {
        SourceConverter converter = new SourceConverter(store, cache);
        converter.convert(cu, cu, loc);
        for (Object cm: cu.getCommentList()) {
            Comment comment = (Comment)cm;
            // Issue 720: changed condition to only visit comments without a parent (includes line, block and misplaced javadoc comments).
            if (comment.getParent() != null)
                continue;
            converter.convert(cu, comment, loc);
        }
        return converter.getModel(true);
    }

    protected String[] translatePaths(ISet paths) {
        String[] result = new String[paths.size()];
        int i = 0;
        for (IValue p : paths) {
            ISourceLocation loc = (ISourceLocation)p;
            if (!loc.getScheme().equals("file")) {
                throw RascalRuntimeException.io(VF.string("all path entries must have the file:/// scheme: " + loc), null);
            }
            result[i++] = loc.getPath();
        }
        return result;
    }

    public IValue createM3FromString(ISourceLocation loc, IString contents, ISet sourcePath, ISet classPath, IString javaVersion, IEvaluatorContext eval) {
        try {
            CompilationUnit cu = getCompilationUnit(loc.getPath(), contents.getValue().toCharArray(), true, javaVersion, translatePaths(sourcePath), translatePaths(classPath));

            TypeStore store = new TypeStore();
            store.extendStore(eval.getHeap().getModule("lang::java::m3::Core").getStore());
            store.extendStore(eval.getHeap().getModule("lang::java::m3::AST").getStore());

            return convertToM3(store, new HashMap<>(), loc, cu);
        } catch (IOException e) {
            throw RuntimeExceptionFactory.io(VF.string(e.getMessage()), null, null);
        }
    }

    /*
     * Creates Rascal ASTs for Java source files
     */
    public IValue createAstsFromFiles(ISet files, IBool collectBindings, ISet sourcePath, ISet classPath, IString javaVersion,
            IEvaluatorContext eval) {
        try {
            TypeStore store = new TypeStore();
            store.extendStore(eval.getHeap().getModule("lang::java::m3::AST").getStore());
            
            boolean allFilePaths = true;
            for (IValue f : files) {
                allFilePaths &= ((ISourceLocation)f).getScheme().equals("file");
            }
            if (allFilePaths) {
                Map<String, ISourceLocation> pathLookup = new HashMap<>();
                String[] paths = new String[files.size()];
                String[] encodings = new String[files.size()];
                int i = 0;
                for (IValue p : files) {
                    ISourceLocation loc = (ISourceLocation)p;
                    paths[i] = loc.getPath();
                    pathLookup.put(loc.getPath(), loc);
                    encodings[i] = guessEncoding(loc);
                    i++;
                }

                Map<String, ISourceLocation> cache = new HashMap<>();
                ISetWriter result = VF.setWriter();
                getCompilationUnits(paths, encodings, collectBindings.getValue(), javaVersion, translatePaths(sourcePath), translatePaths(classPath), (loc, ast) -> {
                    result.insert(convertToAST(collectBindings, cache, pathLookup.get(loc), ast, store));
                });
                return result.done();
            }
            else {
                Map<String, ISourceLocation> cache = new HashMap<>();
                ISetWriter result = VF.setWriter();
                for (IValue file: files) {
                    ISourceLocation loc = (ISourceLocation) file;
                    CompilationUnit cu = getCompilationUnit(loc.getPath(), getFileContents(loc, eval), collectBindings.getValue(), javaVersion, translatePaths(sourcePath), translatePaths(classPath));
                    result.insert(convertToAST(collectBindings, cache, loc, cu, store));
                }
                return result.done();
            }
        } catch (IOException e) {
            throw RuntimeExceptionFactory.io(VF.string(e.getMessage()), null, null);
        }
    }

    protected String guessEncoding(ISourceLocation loc) {
        try (InputStream file = URIResolverRegistry.getInstance().getInputStream(loc)) {
            return UnicodeDetector.estimateCharset(file).name();
        }
        catch (Throwable _) {
            return null;
        }
    }

    protected IValue convertToAST(IBool collectBindings, Map<String, ISourceLocation> cache, ISourceLocation loc,
            CompilationUnit cu, TypeStore store) {
        ASTConverter converter = new ASTConverter(store, cache, collectBindings.getValue());
        converter.convert(cu, cu, loc);
        converter.insertCompilationUnitMessages(true, null);
        return converter.getValue();
    }


    public IValue createAstFromString(ISourceLocation loc, IString contents, IBool collectBindings,ISet sourcePath, ISet classPath, IString javaVersion,
            IEvaluatorContext eval) {
        try {
            CompilationUnit cu = getCompilationUnit(loc.getPath(), contents.getValue().toCharArray(), collectBindings.getValue(), javaVersion, translatePaths(sourcePath), translatePaths(classPath));

            TypeStore store = new TypeStore();
            store.extendStore(eval.getHeap().getModule("lang::java::m3::AST").getStore());

            return convertToAST(collectBindings, new HashMap<>(), loc, cu, store);
        } catch (IOException e) {
            throw RuntimeExceptionFactory.io(VF.string(e.getMessage()), null, null);
        }
    }
    
    
    protected void getCompilationUnits(String[] fileNames, String[] encodings, boolean resolveBindings, IString javaVersion, String[] sourcePath, String[] classPath, final BiConsumer<String, CompilationUnit> consumeASTs) {
        ASTParser parser = constructASTParser(resolveBindings, javaVersion, sourcePath, classPath);
        parser.createASTs(fileNames, encodings, new String[0], new FileASTRequestor() {
            @Override
            public void acceptAST(String sourceFilePath, CompilationUnit ast) {
                consumeASTs.accept(sourceFilePath, ast);
            }
        }, null);
    }
    

    protected CompilationUnit getCompilationUnit(String unitName, char[] contents, boolean resolveBindings, IString javaVersion, String[] sourcePath, String[] classPath) 
            throws IOException {
        ASTParser parser = constructASTParser(resolveBindings, javaVersion, sourcePath, classPath);
        parser.setUnitName(unitName);
        parser.setSource(contents);
        return (CompilationUnit) parser.createAST(null);
    }

    protected ASTParser constructASTParser(boolean resolveBindings, IString javaVersion, String[] sourcePath, String[] classPath) {
        ASTParser parser = ASTParser.newParser(AST.JLS4);
        parser.setResolveBindings(resolveBindings);
        parser.setBindingsRecovery(true);
        parser.setStatementsRecovery(true);

        Hashtable<String, String> options = new Hashtable<String, String>();

        options.put(JavaCore.COMPILER_SOURCE, javaVersion.getValue());
        options.put(JavaCore.COMPILER_COMPLIANCE, javaVersion.getValue());
        options.put(JavaCore.COMPILER_DOC_COMMENT_SUPPORT, "enabled");

        parser.setCompilerOptions(options);

        parser.setEnvironment(classPath, sourcePath, null, true);
        return parser;
    }

    protected char[] getFileContents(ISourceLocation loc, IEvaluatorContext ctx) throws IOException {
        char[] data;
        Reader textStream = null;

        try {
            textStream = URIResolverRegistry.getInstance().getCharacterReader(loc);
            data = InputConverter.toChar(textStream);
        } finally {
            if (textStream != null) {
                textStream.close();
            }
        }
        return data;
    }
}
