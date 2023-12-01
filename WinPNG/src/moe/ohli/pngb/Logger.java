package moe.ohli.pngb;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 
 * @author harne_
 *
 */
public class Logger {
	public enum L {
		ALL(0), DEBUG(1), INFO(2), WARN(3), ERROR(4), FATAL(5);
		private final int value;
		private L(int value) {
			this.value = value;
		}
		public static L fromInt(int value) {
			for (L level : values()) {
				if (level.value == value) {
					return level;
				}
			}
			return null;
		}
	}
	
	private class PrintStreamWithLevel {
		public PrintStream out;
		public int level;
		public PrintStreamWithLevel(PrintStream out, int level) {
			this.out = out;
			this.level = level;
		}
		public void println(L level, String msg) {
			if (level.value >= (this.level < 0 ? defaultLevel : this.level)) {
				out.println(msg);
			}
		}
		public void printStackTrace(L level, Exception e) {
			if (level.value >= this.level) {
				e.printStackTrace(out);
			}
		}
	}
	
	private List<PrintStreamWithLevel> outs = new ArrayList<>();
	private int defaultLevel = L.INFO.value;
	
	public Logger() {
		add(System.out);
	}
	public Logger(L level) {
		this.defaultLevel = level.value;
		add(System.out);
	}
	
	public boolean add(PrintStream out) {
		return outs.add(new PrintStreamWithLevel(out, -1));
	}
	public boolean set(PrintStream out, L level) {
		for (PrintStreamWithLevel pswl : outs) {
			if (pswl.out == out) {
				pswl.level = level.value;
				return true;
			}
		}
		return outs.add(new PrintStreamWithLevel(out, level.value));
	}
	public void setDefaultLevel(L level) {
		defaultLevel = level.value;
	}
	public L getDefaultLevel() {
		return L.fromInt(defaultLevel);
	}

	public void log(L level, String msg) {
		for (PrintStreamWithLevel out : outs) {
			out.println(level, msg);
		}
	}
	public void log(L level, Exception e) {
		for (PrintStreamWithLevel out : outs) {
			out.printStackTrace(level, e);
		}
	}
	public void all  (String msg) { log(L.ALL  , msg); }
	public void debug(String msg) { log(L.DEBUG, msg); }
	public void info (String msg) { log(L.INFO , msg); }
	public void warn (String msg) { log(L.WARN , msg); }
	public void error(String msg) { log(L.ERROR, msg); }
	public void fatal(String msg) { log(L.FATAL, msg); }
	public void all  (Exception e) { log(L.ALL  , e); }
	public void debug(Exception e) { log(L.DEBUG, e); }
	public void info (Exception e) { log(L.INFO , e); }
	public void warn (Exception e) { log(L.WARN , e); }
	public void error(Exception e) { log(L.ERROR, e); }
	public void fatal(Exception e) { log(L.FATAL, e); }
}
