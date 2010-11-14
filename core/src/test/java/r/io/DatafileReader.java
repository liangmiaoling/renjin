/*
 * R : A Computer Language for Statistical Data Analysis
 * Copyright (C) 1995, 1996  Robert Gentleman and Ross Ihaka
 * Copyright (C) 1997--2008  The R Development Core Team
 * Copyright (C) 2003, 2004  The R Foundation
 * Copyright (C) 2010 bedatadriven
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package r.io;

import hep.io.xdr.XDRInputStream;
import r.lang.*;
import r.lang.exception.EvalException;
import r.parser.ParseUtil;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class DatafileReader {

  public static final String ASCII_FORMAT = "RDA2\n";
  public static final String BINARY_FORMAT = "RDB2\n";
  public static final String XDR_FORMAT = "RDX2\n";

  public static final int  NILSXP	  =   0;  /* nil = NULL */
  public static final int  SYMSXP	  =   1;	  /* symbols */
  public static final int  LISTSXP	 =    2;	  /* lists of dotted pairs */
  public static final int  CLOSXP	   =  3;	  /* closures */
  public static final int  ENVSXP	   =  4	;  /* environments */
  public static final int  PROMSXP	  =   5	;  /* promises: [un]evaluated closure arguments */
  public static final int  LANGSXP	  =   6;	  /* language constructs (special lists) */
  public static final int  SPECIALSXP =  7;	  /* special forms */
  public static final int  BUILTINSXP =  8;	  /* builtin non-special forms */
  public static final int  CHARSXP	  =   9;	  /* "scalar" string type (internal only)*/
  public static final int  LGLSXP	   = 10	;  /* logical vectors */
  public static final int  INTSXP	   = 13;	  /* integer vectors */
  public static final int  REALSXP	 =   14	;  /* real variables */
  public static final int  CPLXSXP	 =   15;	  /* complex variables */
  public static final int  STRSXP	   = 16	;  /* string vectors */
  public static final int  DOTSXP	   = 17	;  /* dot-dot-dot object */
  public static final int  ANYSXP	   = 18;	  /* make "any" args work.
			     Used in specifying types for symbol
			     registration to mean anything is okay  */
  public static final int  VECSXP	  =  19;	  /* generic vectors */
  public static final int  EXPRSXP	=    20;	  /* expressions vectors */
  public static final int  BCODESXP  =  21;    /* byte code */
  public static final int  EXTPTRSXP  = 22;    /* external pointer */
  public static final int  WEAKREFSXP = 23;    /* weak reference */
  public static final int  RAWSXP     = 24;    /* raw bytes */
  public static final int  S4SXP      = 25;    /* S4, non-vector */

  public static final int  FUNSXP    =  99;    /* Closure or Builtin or Special */



  public static final int REFSXP =           255 ;
  public static final int  NILVALUE_SXP  =    254 ;
  public static final int  GLOBALENV_SXP  =   253 ;
  public static final int  UNBOUNDVALUE_SXP =  252;
  public static final int  MISSINGARG_SXP =   251;
  public static final int  BASENAMESPACE_SXP= 250;
  public static final int  NAMESPACESXP=      249;
  public static final int  PACKAGESXP  =      248;
  public static final int  PERSISTSXP   =     247;
  /* the following are speculative--we may or may not need them soon */
  public static final int  CLASSREFSXP  =     246;
  public static final int  GENERICREFSXP  =   245;
  public static final int  EMPTYENV_SXP	= 242;
  public static final int  BASEENV_SXP	=  241;

  private static final int CE_NATIVE = 0;
  private static final int CE_UTF8   = 1;
  private static final int CE_LATIN1 = 2;
  private static final int CE_SYMBOL = 5;
  private static final int CE_ANY    =99;

  private static final int LATIN1_MASK  = (1<<2);
  private static final int UTF8_MASK = (1<<3);
  private static final int CACHED_MASK = (1<<5);
  private static final int  HASHASH_MASK =  1;

  private final EnvExp rho;
  private InputStream conn;
  private StreamReader in;

  private int version;
  private Version writerVersion;
  private Version releaseVersion;

  private List<SEXP> referenceTable;

  public DatafileReader(EnvExp rho, InputStream conn) {
    this.rho = rho;
    this.conn = conn;
    this.referenceTable = new ArrayList<SEXP>();
  }

  public SEXP readFile() throws IOException {

    String format = readHeader(conn);
    in = createStreamReader(format);
    version = in.readInt();
    writerVersion = new Version(in.readInt());
    releaseVersion = new Version(in.readInt());

    if(version != 2) {
      if(releaseVersion.isExperimental()) {
        throw new IOException(String.format("cannot read unreleased workspace version %d written by experimental R %s",
            version, writerVersion));
      } else {
        throw new IOException(String.format("cannot read workspace version %d written by R %s; need R %s or newer",
            version, releaseVersion, releaseVersion));
      }
    }

    return readExp();
  }


  private static String readHeader(InputStream conn) throws IOException {
    byte bytes[] = new byte[5];
    for(int i=0;i!= bytes.length;++i) {
      bytes[i] = (byte) conn.read();
      if(bytes[i] == -1) {
        throw new EOFException();
      }
    }
    return new String(bytes);
  }

  private StreamReader createStreamReader(String format) throws IOException {
    StreamReader reader;
    if(format.equals(ASCII_FORMAT)) {
      reader = new AsciiReader(conn);
    } else if(format.equals(BINARY_FORMAT)) {
      reader = new BinaryReader(conn);
    } else if(format.equals(XDR_FORMAT)) {
      reader = new XdrReader(conn);
    } else {
      throw new EvalException("Invalid Format: '%s", format);
    }
    return reader;
  }

  private SEXP readExp() throws IOException {

    Flags flags = new Flags(in.readInt());

    switch(flags.type) {
      case NILVALUE_SXP:
        return NullExp.INSTANCE;
      case EMPTYENV_SXP:
        return EmptyEnv.INSTANCE;
      case BASEENV_SXP:
        return rho.getBaseEnvironment();
      case GLOBALENV_SXP:
        return rho.getGlobalEnvironment();
      case UNBOUNDVALUE_SXP:
        return SymbolExp.UNBOUND_VALUE;
      case MISSINGARG_SXP:
        return SymbolExp.MISSING_ARG;
      case BASENAMESPACE_SXP:
        throw new IOException("name spaces not supported");
      case REFSXP:
        return readReference(flags);
      case PERSISTSXP:
        return readPersistExp();
      case SYMSXP:
        return readSymbol();
      case PACKAGESXP:
        return readPackage();
      case NAMESPACESXP:
        return readNamespace();
      case ENVSXP:
        return readEnv();
      case LISTSXP:
        return readPairList(flags);
      case LANGSXP:
        return readLangExp(flags);
      case CLOSXP:
        return readClosure(flags);
      case PROMSXP:
        return readPromise(flags);
      case DOTSXP:
        return readDotExp(flags);
      case EXTPTRSXP:
        return readExternalPointer(flags);
      case WEAKREFSXP:
        return readWeakReference(flags);
      case SPECIALSXP:
      case BUILTINSXP:
        return readPrimitive(flags);
      case CHARSXP:
        return readCharExp(flags);
      case LGLSXP:
        return readLogical(flags);
      case INTSXP:
        return readIntegerExp(flags);
      case REALSXP:
        return readDoubleExp(flags);
      case CPLXSXP:
        return readComplexExp(flags);
      case STRSXP:
        return readStringExp(flags);
      case VECSXP:
        return readListExp(flags);
      case EXPRSXP:
        return readExpExp(flags);
      case BCODESXP:
        throw new IOException("Byte code expressions are not supported.");
      case CLASSREFSXP:
        throw new IOException("this version of R cannot read class references");
      case GENERICREFSXP:
        throw new IOException("this version of R cannot read generic function references");
      case RAWSXP:
        throw new IOException("this version of R cannot read RAWSXP");
      case S4SXP:
        return readS4XP();
      default:
        throw new IOException(String.format("ReadItem: unknown type %d, perhaps written by later version of R", flags.type));
    }
  }



  private SEXP readPromise(Flags flags) throws IOException {
    throw new IOException("readPromise");
  }

  private SEXP readClosure(Flags flags) throws IOException {
    PairList attributes = readAttributes(flags);
    EnvExp env = (EnvExp) readTag(flags);
    PairList formals = (PairList) readExp();
    SEXP body =  readExp();

    return new ClosureExp(env, formals, body, attributes);
  }

  private SEXP readLangExp(Flags flags) throws IOException {
    PairList attributes = readAttributes(flags);
    SEXP tag = readTag(flags);
    SEXP function = readExp();
    PairList arguments = (PairList) readExp();
    return new LangExp(function, arguments, attributes, tag);
  }

  private SEXP readDotExp(Flags flags) throws IOException {
    throw new IOException("readDotExp not impl");
  }

  private SEXP readPairList(Flags flags) throws IOException {
    PairList attributes = (PairList) readAttributes(flags);
    SEXP tag = readTag(flags);
    SEXP value = readExp();
    PairList nextNode = (PairList) readExp();

    return new PairListExp(tag, value, attributes, nextNode);
  }

  private SEXP readTag(Flags flags) throws IOException {
    return flags.hasTag ? readExp() : NullExp.INSTANCE;
  }

  private PairList readAttributes(Flags flags) throws IOException {
    return flags.hasAttributes ? (PairList)readExp() : NullExp.INSTANCE;
  }

  private SEXP readPackage() throws IOException {
    throw new IOException("package");
  }

  private SEXP readReference(Flags flags) throws IOException {
    int i = readReferenceIndex(flags);
    return referenceTable.get(i);
  }

  private int readReferenceIndex(Flags flags) throws IOException {
    int i = flags.unpackRefIndex();
    if (i == 0)
      return in.readInt() - 1;
    else
      return i - 1;
  }

  private SEXP readSymbol() throws IOException {
    CharExp printName = (CharExp) readExp();
    SymbolExp symbol = new SymbolExp(printName.getValue());
    addReadRef(symbol);
    return symbol;
  }

  private void addReadRef(SEXP value) {
    referenceTable.add(value);
  }

  private SEXP readNamespace() throws IOException {
    throw new IOException("naemspace nto tyimpl");
  }

  private SEXP readEnv() throws IOException {
    int locked = in.readInt();

    EnvExp env = EnvExp.createChildEnvironment(rho); // temporarily assign parent to rho
    addReadRef(env);
                                            
    SEXP parent = readExp();
    SEXP frame = readExp();
    SEXP hashtab = readExp();
    SEXP attributes = readExp();

    env.setParent( parent == NullExp.INSTANCE ? EmptyEnv.INSTANCE : (EmptyEnv)parent);

    return env;

  }

  private SEXP readS4XP() throws IOException {
    throw new IOException("not yet impl");
  }

  private SEXP readListExp(Flags flags) throws IOException {
    return new ListExp(readExpArray(), readAttributes(flags));
  }

  private SEXP readExpExp(Flags flags) throws IOException {
    return new ExpExp(readExpArray(), readAttributes(flags));
  }

  private SEXP[] readExpArray() throws IOException {
    int length = in.readInt();
    SEXP values[] = new SEXP[length];
    for(int i=0;i!=length;++i) {
      values[i] = readExp();
    }
    return values;
  }

  private SEXP readStringExp(Flags flags) throws IOException {
    int length = in.readInt();
    String[] values = new String[length];
    for(int i=0;i!=length;++i) {
      values[i] = ((CharExp)readExp()).getValue();
    }
    return new StringExp(values, readAttributes(flags));
  }

  private SEXP readComplexExp(Flags flags) throws IOException {
    throw new IOException("complex not y i ");
  }

  private SEXP readDoubleExp(Flags flags) throws IOException {
    int length = in.readInt();
    double[] values = new double[length];
    for(int i=0;i!=length;++i) {
      values[i] = in.readDouble();
    }
    return new DoubleExp(values, readAttributes(flags));
  }

  private SEXP readIntegerExp(Flags flags) throws IOException {
    int length = in.readInt();
    int[] values = new int[length];
    for(int i=0;i!=length;++i) {
      values[i] = in.readInt();
    }
    return new IntExp(values, readAttributes(flags));
  }


  private SEXP readLogical(Flags flags) throws IOException {
    int length = in.readInt();
    int values[] = new int[length];
    for(int i=0;i!=length;++i) {
      values[i] = in.readInt();
    }
    return new LogicalExp(values, readAttributes(flags));
  }

  private SEXP readCharExp(Flags flags) throws IOException {
    int length = in.readInt();

    if (length == -1) {
      return new CharExp(StringExp.NA );
    } else  {
      byte buf[] = in.readString(length);
      if(flags.isUTF8Encoded()) {
        return new CharExp(new String(buf, "UTF8"));
      } else if(flags.isLatin1Encoded()) {
        return new CharExp(new String(buf, "Latin1"));
      } else {
        return new CharExp(new String(buf));
      }
    }
  }


  private SEXP readPrimitive(Flags flags) throws IOException {
    throw new IOException("readPrim ");
  }

  private SEXP readWeakReference(Flags flags) throws IOException {
    throw new IOException("weakRef not yet impl");
  }

  private SEXP readExternalPointer(Flags flags) throws IOException {
    throw new IOException("readExternalPointer not yet implemented");
  }

  private SEXP readPersistExp() throws IOException {
    throw new IOException("readPersistExp not yet implemented");
  }


  private interface StreamReader {
    int readInt() throws IOException;
    byte[] readString(int length) throws IOException;
    double readDouble() throws IOException;
  }

  private static class BinaryReader implements StreamReader {

    private final DataInput in;

    private BinaryReader(DataInputStream in) throws IOException {
      this.in = in;
      if(in.readByte() != 'B') {
        throw new IOException("format does not match binary");
      }
      if(in.readByte() == -1) {
        throw new EOFException();
      }

    }
    private BinaryReader(InputStream in) throws IOException {
      this(new DataInputStream(in));
    }

    public int readInt() throws IOException {
      return in.readInt();
    }

    @Override
    public byte[] readString(int length) throws IOException {
      byte buf[] = new byte[length];
      in.readFully(buf);
      return buf;
    }

    @Override
    public double readDouble() throws IOException {
      return in.readDouble();
    }
  }

  private static class AsciiReader implements StreamReader {

    private BufferedReader reader;

    private AsciiReader(BufferedReader reader) {
      this.reader = reader;
    }

    private AsciiReader(InputStream in) {
      this(new BufferedReader(new InputStreamReader(in)));
    }

    public String readWord() throws IOException {
      int codePoint;
      do {
        codePoint = reader.read();
        if(codePoint == -1) {
          throw new EOFException();
        }
      } while(Character.isWhitespace(codePoint));

      StringBuilder sb = new StringBuilder();
      while(!Character.isWhitespace(codePoint)) {
        sb.appendCodePoint(codePoint);
        codePoint = reader.read();
      }
      return sb.toString();
    }

    @Override
    public int readInt() throws IOException {
      String word = readWord();
      if("NA".equals(word)) {
        return IntExp.NA;
      } else {
        return Integer.parseInt(word);
      }
    }

    @Override
    public double readDouble() throws IOException {
      String word = readWord();
      if("NA".equals(word)){
        return DoubleExp.NA;
      } else if("Inf".equals(word)) {
        return Double.POSITIVE_INFINITY;
      } else if("-Inf".equals(word)){
        return Double.NEGATIVE_INFINITY;
      } else {
        return ParseUtil.parseDouble(word);
      }
    }

    @Override
    public byte[] readString(int length) throws IOException {
//if (length > 0) {
//	    int c, d, i, j;
//	    struct R_instring_stream_st iss;
//
//	    InitInStringStream(&iss, stream);
//	    while(isspace(c = GetChar(&iss)))
//		;
//	    UngetChar(&iss, c);
//	    for (i = 0; i < length; i++) {
//		if ((c =  GetChar(&iss)) == '\\') {
//		    switch(c = GetChar(&iss)) {
//		    case 'n' : buf[i] = '\n'; break;
//		    case 't' : buf[i] = '\t'; break;
//		    case 'v' : buf[i] = '\v'; break;
//		    case 'b' : buf[i] = '\b'; break;
//		    case 'r' : buf[i] = '\r'; break;
//		    case 'f' : buf[i] = '\f'; break;
//		    case 'a' : buf[i] = '\a'; break;
//		    case '\\': buf[i] = '\\'; break;
//		    case '?' : buf[i] = '\?'; break;
//		    case '\'': buf[i] = '\''; break;
//		    case '\"': buf[i] = '\"'; break; /* closing " for emacs */
//		    case '0': case '1': case '2': case '3':
//		    case '4': case '5': case '6': case '7':
//			d = 0; j = 0;
//			while('0' <= c && c < '8' && j < 3) {
//			    d = d * 8 + (c - '0');
//			    c = GetChar(&iss);
//			    j++;
//			}
//			buf[i] = d;
//			UngetChar(&iss, c);
//			break;
//		    default  : buf[i] = c;
//		    }
//		}
//		else buf[i] = c;
      throw new IOException("reading strings from ascii file not yet impl");
    }
  }

  private static class XdrReader implements StreamReader {
    private final XDRInputStream in;

    private XdrReader(XDRInputStream in) throws IOException {
      this.in = in;

      if(in.readByte() != 'X') {
        throw new IOException("input format does not match XDR");
      }
      if(in.readByte() == -1) {
        throw new EOFException();
      }
    }

    public XdrReader(InputStream conn) throws IOException {
      this(new XDRInputStream(conn));
    }

    @Override
    public int readInt() throws IOException {
      return in.readInt();
    }

    @Override
    public byte[] readString(int length) throws IOException {
      byte buf[] = new byte[length];
      in.readFully(buf);
      return buf;
    }

    @Override
    public double readDouble() throws IOException {
      return in.readDouble();
    }
  }
  public static class Version {
    private int v, p, s;
    private int packed;

    private Version(int packed) {
      this.packed = packed;
      v = this.packed / 65536; packed = packed % 65536;
      p = packed / 256; packed = packed % 256;
      s = packed;
    }

    public boolean isExperimental() {
      return packed < 0;
    }

    @Override
    public String toString() {
      return String.format("%d.%d.%d", v, p, s);
    }
  }

  private static class Flags {
    private final static int IS_OBJECT_BIT_MASK = (1 << 8);
    private final static int HAS_ATTR_BIT_MASK = (1 << 9);
    private final static int HAS_TAG_BIT_MASK = (1 << 10);

    public final int type;
    public final int levels;
    public final boolean isObject;
    public final boolean hasAttributes;
    public final boolean hasTag;
    private final int flags;

    public Flags(int flags) {
      this.flags = flags;
      type = decodeType(flags);
      levels = decodeLevels(flags);
      isObject = (flags & IS_OBJECT_BIT_MASK) != 0;
      hasAttributes = (flags & HAS_ATTR_BIT_MASK) != 0;
      hasTag = (flags & HAS_TAG_BIT_MASK) != 0;
    }

    private int decodeType(int v) {
      return ((v) & 255);
    }

    private int decodeLevels(int v) {
      return ((v) >> 12);
    }

    public boolean isUTF8Encoded() {
      return (levels & UTF8_MASK) != 0;
    }

    public boolean isLatin1Encoded() {
      return (levels & LATIN1_MASK) != 0;
    }

    public int unpackRefIndex() {
      return   ((flags) >> 8);
    }

  }


}