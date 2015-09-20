package org.rascalmpl.library.experiments.Compiler.RVM.Interpreter;

import java.io.PrintWriter;

import org.eclipse.imp.pdb.facts.IBool;
import org.eclipse.imp.pdb.facts.IConstructor;
import org.eclipse.imp.pdb.facts.IList;
import org.eclipse.imp.pdb.facts.IListWriter;
import org.eclipse.imp.pdb.facts.IMap;
import org.eclipse.imp.pdb.facts.ISourceLocation;
import org.eclipse.imp.pdb.facts.IValue;
import org.eclipse.imp.pdb.facts.IValueFactory;
import org.eclipse.imp.pdb.facts.type.TypeStore;
import org.rascalmpl.interpreter.IEvaluatorContext;  // TODO: remove import? NOT YET: Only used as argument of reflective library function
import org.rascalmpl.interpreter.ITestResultListener;
import org.rascalmpl.interpreter.load.RascalSearchPath;
import org.rascalmpl.interpreter.utils.Timing;
import org.rascalmpl.library.experiments.Compiler.RVM.Interpreter.Instructions.Opcode;

public class ExecuteProgram {

	private IValueFactory vf;
	
	private static ITestResultListener testResultListener;

	public ExecuteProgram(IValueFactory vf) {
		this.vf = vf;
	}
	
	static void setTestResultListener(ITestResultListener trl){
		testResultListener = trl;
	}
	
	// Library function to execute a RVMProgram
	// (Interpreter version)

	public IValue executeProgram(
			ISourceLocation rvmProgramLoc,
			IConstructor rvmProgram,
			IList argumentsAsList,
			IBool debug, 
			IBool testsuite, 
			IBool profile, 
			IBool trackCalls, 
			IBool coverage,
			IBool useJVM,
			IBool serialize, 
			IEvaluatorContext ctx
			) {

		RVMExecutable executable = load(rvmProgramLoc, rvmProgram, useJVM, serialize);

		RascalExecutionContext rex = makeRex(executable, debug, testsuite, profile, trackCalls, coverage, useJVM, ctx.getEvaluator().getRascalResolver());
		return executeProgram(executable, argumentsAsList, rex);
	}

	// Library function to execute an RVMProgram
	// (Compiler version)

	public IValue executeProgram(
			ISourceLocation rvmProgramLoc,
			IConstructor rvmProgram,
			IList argumentsAsList,
			IBool debug, 
			IBool testsuite, 
			IBool profile, 
			IBool trackCalls, 
			IBool coverage,
			IBool useJVM,
			IBool serialize, 
			RascalExecutionContext rex
			) {

		RVMExecutable executable = load(rvmProgramLoc, rvmProgram, useJVM, serialize);

		RascalExecutionContext rex2 = makeRex(executable, debug, testsuite, profile, trackCalls, coverage, useJVM, rex.getRascalSearchPath());
		return executeProgram(executable, argumentsAsList, rex2);
	}

	// Library function to link and execute a RVM program from file
	// (Interpreter version)

	public IValue executeProgram(
			ISourceLocation rvmExecutableLoc,
			IList argumentsAsList,
			IBool debug, 
			IBool testsuite, 
			IBool profile, 
			IBool trackCalls, 
			IBool coverage,
			IBool useJVM,
			IEvaluatorContext ctx
			) {

		RVMExecutable executable = load(rvmExecutableLoc);
		RascalExecutionContext rex = makeRex(executable, debug, testsuite, profile, trackCalls, coverage, useJVM, ctx.getEvaluator().getRascalResolver());
		return executeProgram(executable, argumentsAsList, rex);
		}
		
	// Library function to link and execute a RVM program from file
	// (Compiler version)

	public IValue executeProgram(
			ISourceLocation rvmExecutableLoc,
			IList argumentsAsList,
			IBool debug, 
			IBool testsuite, 
			IBool profile, 
			IBool trackCalls, 
			IBool coverage,
			IBool useJVM,
			RascalExecutionContext rex
			) {
		RVMExecutable executable = load(rvmExecutableLoc);
		RascalExecutionContext rex2 = makeRex(executable, debug, testsuite, profile, trackCalls, coverage, useJVM, rex.getRascalSearchPath());
		return executeProgram(executable, argumentsAsList, rex2);
	}
	
	private RascalExecutionContext makeRex(
					RVMExecutable rvmExecutable,
					IBool debug, 
					IBool testsuite, 
					IBool profile, 
					IBool trackCalls, 
					IBool coverage, 
					IBool useJVM, RascalSearchPath rascalSearchPath
	) {
		return new RascalExecutionContext(
					vf, 
				   	new PrintWriter(System.out), 
				   	new PrintWriter(System.err), 
				   	rvmExecutable.getModuleTags(), 
				   	rvmExecutable.getSymbolDefinitions(),
				   	new TypeStore(), 
				   	debug.getValue(), 
				   	testsuite.getValue(), 
				   	profile.getValue(), 
				   	trackCalls.getValue(), 
				   	coverage.getValue(), 
				   	useJVM.getValue(), 
				   	null, 
				   	rascalSearchPath);
	}
	
	
	public RVMExecutable loadProgram(
					 ISourceLocation rvmProgramLoc,
					 IConstructor rvmProgram,
					 IBool useJVM	
    ) {
		
		return load(
					rvmProgramLoc,
				    rvmProgram, 
				    useJVM,
				    vf.bool(false)
				    );
	}
	
	// Read a RVMExecutable from file
	
	public RVMExecutable load(ISourceLocation rvmExecutableLoc) {
		return RVMExecutable.read(rvmExecutableLoc);
	}
	
	// Create an RVMExecutable given an RVMProgram
	
	public RVMExecutable load(
			 	ISourceLocation rvmProgramLoc,
			 	IConstructor rvmProgram,
			 	IBool useJVM, 
			 	IBool serialize
	) {

		TypeStore typeStore = new TypeStore();
		RVMLoader loader = new RVMLoader(vf, typeStore);
		RVMExecutable executable = loader.load(rvmProgram,	useJVM.getValue());
		
		if(serialize.getValue()){
			executable.write(rvmProgramLoc);			

//			/*** Consistency checking after read: TODO: REMOVE THIS WHEN STABLE*/
//			RVMLinked executable2 = RVMLinked.read(linkedRVM);
//			if(!executable.comparable(executable2)){
//				System.err.println("RVMExecutables differ");
//			}
//
//			//TODO: Use the serialized version for testing purposes only
//			executable = executable2;
		}
		return executable;
	}
	
	
	
	
		
	public IValue executeProgram(RVMExecutable executable, IList argumentsAsList, RascalExecutionContext rex){
		RVM rvm = rex.getUseJVM() ? new RVMJVM(executable, rex) : new RVM(executable, rex);
		
		rvm = initializedRVM(executable, rex);
		
		return executeProgram(rvm, executable, argumentsAsList, rex);
	}
	
	/**
	 * @param executable		RVM exectable
	 * @param argumentsAsList	list of actual parameters
	 * @param rex				Execution context
	 * @return					Result of executing program with given parameters in given context
	 */
	public IValue executeProgram(RVM rvm, RVMExecutable executable, IList argumentsAsList, RascalExecutionContext rex){
		
		rex.setRVM(rvm);
		IValue[] arguments = new IValue[argumentsAsList.length()];
		for(int i = 0; i < argumentsAsList.length(); i++){
			arguments[i] = argumentsAsList.get(i);
		}

		try {
			//long start = Timing.getCpuTime();
			IValue result = null;
			if(rex.getTestSuite()){
				/*
				 * Execute as testsuite
				 */
				rvm.executeProgram("TESTSUITE", executable.getUidModuleInit(), arguments, null);

				IListWriter w = vf.listWriter();
				int n = 0;
				for(String uid_testsuite: executable.getTestSuites()){
					//RascalPrimitive.reset();
					System.out.println("Testsuite: " + uid_testsuite);
					IList test_results = (IList)rvm.executeProgram("TESTSUITE" + n++, uid_testsuite, arguments, null);
					w.insertAll(test_results);
				}
				result = w.done();
			} else {
				/*
				 * Standard execution of main function
				 */
				if(executable.getUidModuleMain().equals("")) {
					throw RascalRuntimeException.noMainFunction(null);
				}
				String moduleName = executable.getModuleName();
				rvm.executeProgram(moduleName, executable.getUidModuleInit(), arguments, null);
				//System.out.println("Initializing: " + (Timing.getCpuTime() - start)/1000000 + "ms");
				result = rvm.executeProgram(moduleName, executable.getUidModuleMain(), arguments, null);
			}
			//long now = Timing.getCpuTime();
			MuPrimitive.exit(rvm.getStdOut());
			RascalPrimitive.exit(rex);
			Opcode.exit();
			if(rex.getProfile()){
				((ProfileLocationCollector) rvm.getLocationCollector()).report(rvm.getStdOut());
			} else if(rex.getCoverage()){
				((CoverageLocationCollector) rvm.getLocationCollector()).report(rvm.getStdOut());
			}
			
			//System.out.println("Executing: " + (now - start)/1000000 + "ms");
			return (IValue) result;
			
		} catch(Thrown e) {
			e.printStackTrace(rex.getStdOut());
			return vf.tuple(vf.string("Runtime exception: " + e.value), vf.integer(0));
		}
	}
	
	/**
	 * @param executable	RVM exectable
	 * @return				an initialized RVM instance
	 */
	 public RVM initializedRVM(RVMExecutable executable, RascalExecutionContext rex){
		
		RVM rvm = rex.getUseJVM() ? new RVMJVM(executable, rex) : new RVM(executable, rex);
		
		ProfileLocationCollector profilingCollector = null;
		CoverageLocationCollector coverageCollector = null;
		
		if(rex.getProfile()){
			profilingCollector = new ProfileLocationCollector();
			rvm.setLocationCollector(profilingCollector);
			profilingCollector.start();
	
		} else if(rex.getCoverage()){
			coverageCollector = new CoverageLocationCollector();
			rvm.setLocationCollector(coverageCollector);
		}
		
		// Execute initializers of imported modules
		for(String initializer: executable.getInitializers()){
			rvm.executeProgram("UNDEFINED", initializer, new IValue[0], null);
		}
		
		if(executable.getUidModuleInit().equals("")) {
			// TODO remove collector
			throw new CompilerError("No module_init function found when loading RVM code!");
		}
		
		return rvm;
	}
}
