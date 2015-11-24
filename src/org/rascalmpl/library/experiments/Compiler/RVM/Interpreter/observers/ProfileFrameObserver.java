package org.rascalmpl.library.experiments.Compiler.RVM.Interpreter.observers;

import java.io.PrintWriter;

import org.rascalmpl.library.experiments.Compiler.RVM.Interpreter.Frame;
import org.rascalmpl.library.experiments.Compiler.RVM.Interpreter.RVM;
import org.rascalmpl.value.IList;
import org.rascalmpl.value.ISourceLocation;

public class ProfileFrameObserver implements IFrameObserver {

	private final PrintWriter stdout;
	private volatile ISourceLocation src;
	
	private Profiler profiler;
	
	public ProfileFrameObserver(PrintWriter stdout){
		this.stdout = stdout;
		profiler = new Profiler(this);
		profiler.start();
	}
	
	@Override
	public void observe(Frame frame) {
		this.src = frame.src;
	}
	
	@Override
	public void stopObserving() {
		profiler.stopCollecting();
	}

	@Override
	public void startObserving() {
		profiler.startCollecting();
	}

	public ISourceLocation getLocation() {
		return src;
	}

	public void start() {
		profiler.start();
	}
	
	public void restart(){
		profiler = new Profiler(this, profiler.getRawData());
	}
	
	public void stop() {
		profiler.pleaseStop();
	}

	@Override
	public void report(IList data) {
		profiler.pleaseStop();
		profiler.report(stdout);
	}
	
	@Override
	public void report() {
		profiler.pleaseStop();
		profiler.report(stdout);
	}

	@Override
	public IList getData() {
		profiler.pleaseStop();
		IList data = profiler.getProfile();
		return data;
	}
}