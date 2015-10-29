package konoha.script;

import java.io.IOException;
import java.lang.reflect.Type;

import konoha.main.ConsoleUtils;
import konoha.message.Message;
import konoha.syntax.ScriptContextHacks;
import nez.Parser;
import nez.io.SourceContext;
import nez.lang.GrammarFile;

public class ScriptContext extends ScriptContextHacks {

	public ScriptContext(Parser parser) {
		this.setParser(parser);
		this.typeSystem = new TypeSystem(this);
		this.checker = new TypeChecker(this, getTypeSystem());
		this.eval = new ScriptEvaluator(this, getTypeSystem());
		this.set("__lookup__", getTypeSystem());
		// new TypeChecker2();
	}

	public void setShellMode(boolean b) {
		this.getTypeSystem().setShellMode(b);
	}

	public final void load(String path, GrammarFile g) throws IOException {
		eval(SourceContext.newFileContext(path), g);
	}

	public final Object eval(String uri, int linenum, String script, GrammarFile g) {
		return eval(SourceContext.newStringContext(uri, linenum, script), g);
	}

	ScriptContextError found = ScriptContextError.NoError;

	public final ScriptContextError getError() {
		return found;
	}

	public void found(ScriptContextError e) {
		found = e;
	}

	public final Object eval(SourceContext source, GrammarFile g) {
		this.found = ScriptContextError.NoError;
		SyntaxTree node = (SyntaxTree) this.getParser().parse(source, new SyntaxTree());
		if (node == null) {
			log(source.getErrorMessage("error", Message.SyntaxError.toString()));
			this.found = ScriptContextError.SyntaxError;
			return ScriptEvaluator.empty; // nothing
		}
		if (!node.is(CommonSymbols._Source)) {
			node = node.newInstance(CommonSymbols._Source, node);
		}
		g.desugarTree(node);
		return evalSource(node);
	}

	public boolean enableASTDump = false;

	private Object evalSource(SyntaxTree node) {
		Object result = ScriptEvaluator.empty;
		for (int i = 0; i < node.size(); i++) {
			SyntaxTree sub = node.get(i);
			if (enableASTDump) {
				ConsoleUtils.println("[Parsed]");
				ConsoleUtils.println("    ", sub);
			}
			SyntaxTree typed = checker.checkAtTopLevel(sub);
			if (typed != sub) {
				node.set(i, typed);
			}
			if (enableASTDump) {
				ConsoleUtils.println("[Typed]");
				ConsoleUtils.println("    ", sub);
			}
			if (found == ScriptContextError.NoError) {
				result = eval.visit(typed);
				if (typed.getType() == void.class) {
					result = ScriptEvaluator.empty;
				}
			}
		}
		return found == ScriptContextError.NoError ? result : ScriptEvaluator.empty;
	}

	public Object get(String name) {
		GlobalVariable gv = this.getTypeSystem().getGlobalVariable(name);
		if (gv != null) {
			return Reflector.getStatic(gv.getField());
		}
		return null;
	}

	public void set(String name, Object value) {
		GlobalVariable gv = this.getTypeSystem().getGlobalVariable(name);
		if (gv == null) {
			Type type = Reflector.infer(value);
			gv = this.getTypeSystem().newGlobalVariable(type, name);
		}
		Reflector.setStatic(gv.getField(), value);
	}

	public final void println(Object o) {
		ConsoleUtils.println(o);
	}

	public void log(String msg) {
		int c = msg.indexOf("[error]") > 0 ? 31 : 35;
		ConsoleUtils.begin(c);
		ConsoleUtils.println(msg);
		ConsoleUtils.end();
	}

	private boolean verboseMode = true;

	public void setVerboseMode(boolean b) {
		verboseMode = b;
	}

	public void verbose(String fmt, Object... args) {
		if (verboseMode) {
			ConsoleUtils.begin(37);
			ConsoleUtils.println(String.format(fmt, args));
			ConsoleUtils.end();
		}
	}

}
