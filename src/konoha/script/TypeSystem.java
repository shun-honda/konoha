package konoha.script;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashMap;

import konoha.ArrayInt;
import konoha.DynamicOperator;
import konoha.Function;
import konoha.StaticOperator;
import konoha.StringOperator;
import konoha.asm.ScriptCompiler;
import konoha.hack.Hacker;
import konoha.main.ConsoleUtils;
import nez.ast.Tree;
import nez.util.UList;

public class TypeSystem implements CommonSymbols {
	ScriptContext context;
	ScriptCompiler compl;

	public TypeSystem(ScriptContext context) {
		this.context = context;
		init();
		initDebug();
	}

	void init() {
		loadStaticFunctionClass(DynamicOperator.class, false);
		loadStaticFunctionClass(StaticOperator.class, false);
		loadStaticFunctionClass(StringOperator.class, false);
		loadStaticFunctionClass(konoha.libc.class, false);
		this.setType("void", void.class);
		this.setType("boolean", boolean.class);
		this.setType("byte", byte.class);
		this.setType("char", char.class);
		this.setType("short", int.class);
		this.setType("int", int.class);
		this.setType("long", long.class);
		this.setType("float", double.class);
		this.setType("double", double.class);
		this.setType("String", String.class);
		this.setType("Array", konoha.Array.class);
		this.setType("Dict", konoha.Dict.class);
		this.setType("Func", Function.class);
	}

	public void init(ScriptCompiler compl) {
		this.compl = compl; // this is called when the complier is instatiated
	}

	void initDebug() {
		this.setType("Math", Math.class);
		this.setType("System", System.class);
	}

	/* Types */

	HashMap<String, Type> TypeNames = new HashMap<>();

	public void setType(String name, Type type) {
		this.TypeNames.put(name, type);
	}

	public final Type getType(String name) {
		return this.TypeNames.get(name);
	}

	/* ArrayType */

	public Type newArrayType(Type elementType) {
		return GenericType.newType(konoha.Array.class, elementType);
	}

	public static Type getArrayElementType(Type atype) {
		if (atype instanceof GenericType) {
			return ((GenericType) atype).getParameterTypes()[0];
		}
		if (atype == ArrayInt.class) {
			return int.class;
		}
		return Object.class;
	}

	public static Class<?> getArrayElementClass(Type atype) {
		return toClass(getArrayElementType(atype));
	}

	/* FuncType */

	public Class<?> getFuncType(Class<?> returnType, Class<?>... paramTypes) {
		String name = ScriptCompiler.nameFuncType(returnType, paramTypes);
		Class<?> c = (Class<?>) this.TypeNames.get(name);
		if (c == null) {
			c = this.compl.compileFuncType(name, returnType, paramTypes);
			this.TypeNames.put(name, c);
		}
		return c;
	}

	public Class<?> getFuncType(Type returnType, Type... paramTypes) {
		String name = ScriptCompiler.nameFuncType(returnType, paramTypes);
		Class<?> c = (Class<?>) this.TypeNames.get(name);
		if (c == null) {
			Class<?>[] p = new Class<?>[paramTypes.length];
			for (int i = 0; i < p.length; i++) {
				p[i] = TypeSystem.toClass(paramTypes[i]);
			}
			c = this.compl.compileFuncType(name, TypeSystem.toClass(returnType), p);
			this.TypeNames.put(name, c);
		}
		return c;
	}

	public boolean isFuncType(Type f) {
		return this.isStaticFuncType(f) || this.isDynamicFuncType(f);
	}

	public boolean isDynamicFuncType(Type f) {
		if (f == konoha.Function.class) {
			return true;
		}
		return false;
	}

	public boolean isStaticFuncType(Type f) {
		if (f instanceof Class<?>) {
			return ((Class<?>) f).getSuperclass() == konoha.Function.class;
		}
		return false;
	}

	public final static Class<?> getFuncReturnType(Type f) {
		if (f == konoha.Function.class) {
			return Object.class;
		}
		Method m = Reflector.findInvokeMethod((Class<?>) f);
		return m.getReturnType();
	}

	public final static Class<?>[] getFuncParameterTypes(Type f) {
		Method m = Reflector.findInvokeMethod((Class<?>) f);
		return m.getParameterTypes();
	}

	/* GlobalVariables */

	HashMap<String, GlobalVariable> GlobalVariables = new HashMap<>();

	public boolean hasGlobalVariable(String name) {
		return this.GlobalVariables.containsKey(name);
	}

	public GlobalVariable getGlobalVariable(String name) {
		return this.GlobalVariables.get(name);
	}

	public GlobalVariable newGlobalVariable(Type type, String name) {
		Class<?> varClass = this.compl.compileGlobalVariable(TypeSystem.toClass(type), name);
		GlobalVariable gv = new GlobalVariable(type, varClass);
		this.GlobalVariables.put(name, gv);
		return gv;
	}

	// private GlobalVariable addDebugGlobalVariable(Type type, String name,
	// Class<?> varClass) {
	// GlobalVariable gv = new GlobalVariable(type, varClass);
	// this.GlobalVariables.put(name, gv);
	// return gv;
	// }

	/* SymbolList */
	private UList<Object> symbolList = new UList<Object>(new Object[256]);

	public UList<Object> getSymbolList() {
		return symbolList;
	}

	// private UList<Method> StaticFunctionMethodList = new UList<Method>(new
	// Method[256]);
	private HashMap<String, Method> methodMap = new HashMap<String, Method>();

	private Method getMethodMap(String key) {
		return this.methodMap.get(key);
	}

	private Method setMethodMap(String key, Method method) {
		return this.methodMap.put(key, method);
	}

	private String cast_key(Class<?> f, Class<?> t) {
		return f.getName() + "&" + t.getName();
	}

	public void addCastMethod(Class<?> f, Class<?> t, Method m) {
		// System.out.println("cast: " + cast_key(f, t) + " " + m);
		this.setMethodMap(cast_key(f, t), m);
	}

	public Method getCastMethod(Type f, Type t) {
		return this.getMethodMap(cast_key(TypeSystem.toClass(f), TypeSystem.toClass(t)));
	}

	public Method getCastMethod(Class<?> f, Class<?> t) {
		// System.out.println("cast: " + cast_key(f, t) + " ? ");
		return this.getMethodMap(cast_key(f, t));
	}

	private String convert_key(Class<?> f, Class<?> t) {
		return f.getName() + "!" + t.getName();
	}

	public void addConvertMethod(Class<?> f, Class<?> t, Method m) {
		this.setMethodMap(convert_key(f, t), m);
	}

	public Method getConvertMethod(Class<?> f, Class<?> t) {
		return this.getMethodMap(convert_key(f, t));
	}

	public void loadStaticFunctionClass(Class<?> c, boolean isGenerated) {
		// StaticFunctionMethodList.add(c);
		for (Method m : c.getMethods()) {
			// Annotation[] a = m.getAnnotations();
			// for (Annotation aa : a) {
			// System.out.println("aa=" + aa);
			// }
			if (isStatic(m)) {
				String name = m.getName();
				if (name.startsWith("to")) {
					Class<?>[] p = m.getParameterTypes();
					if (p.length == 1) {
						Class<?> f = p[0];
						Class<?> t = m.getReturnType();
						if (name.startsWith("to_")) {
							addCastMethod(f, t, m);
						} else {
							addConvertMethod(f, t, m);
						}
					}
					if (!isGenerated) {
						continue;
					}
				}
				this.symbolList.add(m);
			}
		}
	}

	public void importStaticClass(String path) throws ClassNotFoundException {
		Class<?> c = Class.forName(path);
		if (Hacker.class.isAssignableFrom(c)) {
			Hacker hack = (Hacker) Reflector.newInstance(c);
			hack.perform(context, this);
		} else {
			loadStaticFunctionClass(c, false);
			this.setType(c.getSimpleName(), c);
		}
	}

	/**
	 * Resolving method
	 * 
	 */

	// private Method matchMethod(Class<?> c, boolean isStaticOnly,
	// TypeVarMatcher matcher, String name, Type[] types, UList<Method> buf) {
	// for (Method m : isStaticOnly ? c.getMethods() : c.getDeclaredMethods()) {
	// if (Modifier.isPublic(m.getModifiers())) {
	// if (matchMethod(m, isStaticOnly, matcher, name, types, buf)) {
	// return m;
	// }
	// }
	// }
	// return null;
	// }
	//
	// private boolean matchMethod(Method m, boolean isStaticOnly,
	// TypeVarMatcher matcher, String name, Type[] types, UList<Method> buf) {
	// if (isStaticOnly && !this.isStatic(m)) {
	// return false;
	// }
	// if (!name.equals(m.getName())) {
	// return false;
	// }
	// Class<?>[] p = m.getParameterTypes();
	// if (p.length != types.length) {
	// return false;
	// }
	// if (matcher == null || !isGenericMethod(m)) {
	// if (this.acceptParameters(null, p, types)) {
	// return true;
	// }
	// } else {
	// Type[] gp = m.getGenericParameterTypes();
	// if (this.acceptParameters(matcher, gp, types)) {
	// return true;
	// }
	// matcher.reset();
	// }
	// if (buf != null) {
	// buf.add(m);
	// }
	// return false;
	// }

	private boolean isGenericMethod(Method m) {
		Type r = m.getGenericReturnType();
		if (!(r instanceof Class<?>)) {
			return true;
		}
		for (Type t : m.getGenericParameterTypes()) {
			if (!(t instanceof Class<?>)) {
				return true;
			}
		}
		return false;
	}

	// private boolean acceptParameters(TypeVarMatcher matcher, Type[] p, Type[]
	// types) {
	// for (int j = 0; j < types.length; j++) {
	// if (!accept(matcher, p[j], types[j])) {
	// return false;
	// }
	// }
	// return true;
	// }
	//
	// public final boolean accept(TypeVarMatcher matcher, Type p, Type a) {
	// if (a == null || p == a) {
	// return true;
	// }
	// if (p instanceof Class<?> || matcher == null) {
	// if (((Class<?>) p).isAssignableFrom(TypeSystem.toClass(a))) {
	// return true;
	// }
	// return false;
	// }
	// return matcher.match(p, a);
	// }
	//
	// private Method checkMethodTypeEnforcement(int start, UList<Method> buf,
	// TypeVarMatcher matcher, TypedTree params) {
	// if (buf != null && start < buf.size()) {
	// TypedTree[] results = new TypedTree[params.size()];
	// for (int j = start; j < buf.size(); j++) {
	// Method m = buf.ArrayValues[j];
	// Arrays.fill(results, null);
	// Type[] p = m.getParameterTypes();
	// if (matcher != null && isGenericMethod(m)) {
	// p = m.getGenericParameterTypes();
	// }
	// if (this.matchParameters(results, matcher, p, params)) {
	// for (int i = 0; i < results.length; i++) {
	// params.set(i, results[i]);
	// }
	// buf.clear(start);
	// return m;
	// }
	// }
	// }
	// return null;
	// }
	//
	// private boolean matchParameters(TypedTree[] results, TypeVarMatcher
	// matcher, Type[] p, TypedTree params) {
	// for (int i = 0; i < p.length; i++) {
	// TypedTree sub = params.get(i);
	// results[i] = matcher != null ? this.checkType(matcher, p[i], sub) :
	// this.checkType(p[i], sub);
	// if (results[i] == null) {
	// if (matcher != null) {
	// matcher.reset();
	// }
	// return false;
	// }
	// }
	// return true;
	// }
	//
	// public final TypedTree checkType(TypeVarMatcher matcher, Type reqt,
	// TypedTree node) {
	// if (Interface.accept(matcher, reqt, node.getType())) {
	// return node;
	// }
	// Type resolved = matcher.resolve(reqt, null);
	// if (resolved != null) {
	// return this.tryTypeCoersion(reqt, resolved, node);
	// }
	// return null;
	// }
	//
	//
	// public TypedTree checkType(Type reqt, TypedTree node) {
	// Type expt = node.getType();
	// if (accept(null, reqt, expt)) {
	// return node;
	// }
	// return tryTypeCoersion(reqt, expt, node);
	// }

	// /* static method */
	//
	// public Method resolveStaticMethod(Class<?> c, String name, Type[] types,
	// UList<Method> buf, TypedTree params) {
	// int start = buf != null ? buf.size() : 0;
	// Method m = matchMethod(c, true/* StaticOnly */, null, name, types, buf);
	// if (m != null) {
	// return m;
	// }
	// return this.checkMethodTypeEnforcement(start, buf, null, params);
	// }
	//
	// // function, operator
	//
	// public Method resolveFunctionMethod(String name, Type[] types,
	// UList<Method> buf, TypedTree params) {
	// int start = buf != null ? buf.size() : 0;
	// for (int i = StaticFunctionMethodList.size() - 1; i >= 0; i--) {
	// Method m = StaticFunctionMethodList.ArrayValues[i];
	// if (this.matchMethod(m, true, null, name, types, buf)) {
	// return m;
	// }
	// }
	// return this.checkMethodTypeEnforcement(start, buf, null, params);
	// }
	//
	// // object method
	//
	// public Method resolveObjectMethod(Type t, TypeVarMatcher matcher, String
	// name, Type[] types, UList<Method> buf, TypedTree params) {
	// int start = buf != null ? buf.size() : 0;
	// Class<?> c = TypeSystem.toClass(t);
	// while (c != null) {
	// Method m = this.matchMethod(c, false, matcher, name, types, buf);
	// if (m != null) {
	// return m;
	// }
	// if (c == Object.class) {
	// break;
	// }
	// c = c.getSuperclass();
	// }
	// return this.checkMethodTypeEnforcement(start, buf, matcher, params);
	// }

	// interface

	// type check

	public static final String name(Type t) {
		if (t == null) {
			return "untyped";
		}
		if (t instanceof Class<?>) {
			String n = ((Class<?>) t).getName();
			if (n.startsWith("java.lang.") || n.startsWith("java.util.")) {
				return n.substring(10);
			}
			return n;
		}
		return t.toString();
	}

	public String reportWarning(TypedTree node, String msg) {
		msg = node.formatSourceMessage("warning", msg);
		context.log(msg);
		return msg;
	}

	public String reportWarning(TypedTree node, String fmt, Object... args) {
		return reportWarning(node, String.format(fmt, args));
	}

	public final Type PrimitiveType(Type t) {
		if (t == Double.class || t == Float.class || t == float.class) {
			return double.class;
		}
		if (t == Long.class) {
			return long.class;
		}
		if (t == Integer.class || t == Short.class || t == short.class) {
			return int.class;
		}
		if (t == Character.class) {
			return char.class;
		}
		if (t == Character.class) {
			return char.class;
		}
		if (t == Boolean.class) {
			return boolean.class;
		}
		if (t == Byte.class) {
			return byte.class;
		}
		return t;
	}

	public static interface BinaryTypeUnifier {
		Type unify(Type t, Type t2);
	}

	private static class Additive implements BinaryTypeUnifier {
		@Override
		public Type unify(Type t, Type t2) {
			if (t == t2) {
				return t;
			}
			if (t == Object.class || t2 == Object.class) {
				return Object.class;
			}
			if (t == BigDecimal.class || t2 == BigDecimal.class) {
				return BigDecimal.class;
			}
			if (t == BigInteger.class || t2 == BigInteger.class) {
				return BigInteger.class;
			}
			if (t == double.class || t2 == double.class) {
				return double.class;
			}
			if (t == long.class || t2 == long.class) {
				return long.class;
			}
			if (t == int.class || t2 == int.class || t == byte.class || t2 == byte.class) {
				return int.class;
			}
			return t;
		}
	}

	private static class Equator implements BinaryTypeUnifier {
		@Override
		public Type unify(Type t, Type t2) {
			if (t == t2) {
				return t;
			}
			if (t == Object.class || t2 == Object.class) {
				return Object.class;
			}
			if (t == BigDecimal.class || t2 == BigDecimal.class) {
				return BigDecimal.class;
			}
			if (t == BigInteger.class || t2 == BigInteger.class) {
				return BigInteger.class;
			}
			if (t == double.class || t2 == double.class) {
				return double.class;
			}
			if (t == long.class || t2 == long.class) {
				return long.class;
			}
			if (t == int.class || t2 == int.class) {
				return int.class;
			}
			return t;
		}
	}

	private static class TComparator implements BinaryTypeUnifier {
		@Override
		public Type unify(Type t, Type t2) {
			if (t == t2) {
				return t;
			}
			if (t == BigDecimal.class || t2 == BigDecimal.class) {
				return BigDecimal.class;
			}
			if (t == BigInteger.class || t2 == BigInteger.class) {
				return BigInteger.class;
			}
			if (t == double.class || t2 == double.class) {
				return double.class;
			}
			if (t == long.class || t2 == long.class) {
				return long.class;
			}
			if (t == int.class || t2 == int.class) {
				return int.class;
			}
			return t;
		}
	}

	private static class Bitwise implements BinaryTypeUnifier {
		@Override
		public Type unify(Type t, Type t2) {
			if (t == Object.class || t2 == Object.class) {
				return Object.class;
			}
			if (t == BigInteger.class || t2 == BigInteger.class) {
				return BigInteger.class;
			}
			if (t == long.class || t2 == long.class) {
				return long.class;
			}
			if (t == int.class || t2 == int.class) {
				return int.class;
			}
			return t;
		}
	}

	public static BinaryTypeUnifier UnifyAdditive = new Additive();
	public static BinaryTypeUnifier UnifyEquator = new Equator();
	public static BinaryTypeUnifier UnifyComparator = new TComparator();
	public static BinaryTypeUnifier UnifyBitwise = new Bitwise();

	// typeof

	public Class<?> typeof(Tree<?> node) {
		if (node instanceof TypedTree) {
			Class<?> type = ((TypedTree) node).getClassType();
			if (type != null) {
				return type;
			}
		}
		return Object.class; // untyped
	}

	public final boolean isStatic(Method m) {
		return Modifier.isStatic(m.getModifiers());
	}

	public final static Class<?> toClass(Type type) {
		if (type instanceof Class<?>) {
			return (Class<?>) type;
		}
		return ((GenericType) type).base;
	}

	public Field getField(Class<?> c, String name) {
		try {
			Field f = c.getField(name);
			if (Modifier.isPublic(f.getModifiers())) {
				return f;
			}
		} catch (NoSuchFieldException e) {
		} catch (SecurityException e) {
		}
		return null;
	}

	public boolean isDynamic(Type c) {
		return c == Object.class;
	}

	public Type dynamicType() {
		return Object.class;
	}

	// debug

	protected boolean shellMode = false;
	protected boolean verboseMode = false;
	protected boolean debugMode = false;

	public void setShellMode(boolean b) {
		this.shellMode = b;
	}

	public void setVerboseMode(boolean b) {
		this.verboseMode = b;
	}

	public void setDebugMode(boolean b) {
		this.debugMode = b;
	}

	public final boolean isVerboseMode() {
		return this.verboseMode;
	}

	public void TRACE(String fmt, Object... args) {
		if (this.verboseMode) {
			System.err.println("TRACE: " + String.format(fmt, args));
		}
	}

	public void TODO(String fmt, Object... args) {
		if (this.verboseMode) {
			ConsoleUtils.println("TODO: " + String.format(fmt, args));
		}
	}

	public void DEBUG(String fmt, Object... args) {
		// if (this.debugMode) {
		System.err.println("DEBUG: " + String.format(fmt, args));
		// }
	}

}