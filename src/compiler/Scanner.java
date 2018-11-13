package compiler;

import java.io.BufferedReader;
import java.io.IOException;

/**
 *�����ʷ�����������Ĺ����Ǵ�Դ���������ȡ�ķ����ţ�����PL/0����������Ҫ��ɲ���֮һ��
 */

public class Scanner {
	/**
	 * �ոն�����ַ�
	 */
	private char justReadChar = ' ';
	
	/**
	 * ��ǰ�������
	 */
	private char[] currentLine;
	
	/**
	 * ��ǰ�еĳ��ȣ�currentLine length��
	 */
	public int currentLineLength = 0;
	
	/**
	 * ��ǰ�ַ��ڵ�ǰ���е�λ�ã�character counter��
	 */
	public int charCounter = 0;
	
	/**
	 * ��ǰ����ķ���
	 */
	public Symbol currentSymbol;
	
	/**
	 * �������б�ע�Ᵽ���ֵĴ��˳��
	 */
	private String[] keyword;
	
	/**
	 * �����ֶ�Ӧ�ķ���ֵ
	 */
	private Symbol[] keywordTable;
	
	/**
	 * ���ַ��ķ���ֵ
	 */
	private Symbol[] charTable;

	// ������
	private BufferedReader inReader;

	/**
	 * ��ʶ�����֣������ǰ�����Ǳ�ʶ���Ļ���
	 * @see Parser
	 * @see Table#enter
	 */
	public String id;

	/**
	 * ��ֵ��С�������ǰ���������ֵĻ���
	 * @see Parser
	 * @see Table#enter
	 */
	public int num;
	
	/**
	 * ��ʼ���ʷ�������
	 * @param input PL/0 Դ�ļ�������
	 */
	public Scanner(BufferedReader input) {
		inReader = input;
		
		// ���õ��ַ�����
		charTable = new Symbol[256];
		java.util.Arrays.fill(charTable, Symbol.nul);
		charTable['+'] = Symbol.plus;
		charTable['-'] = Symbol.minus;
		charTable['*'] = Symbol.times;
		charTable['/'] = Symbol.slash;
		charTable['('] = Symbol.lparen;
		charTable[')'] = Symbol.rparen;
		charTable['='] = Symbol.eql;
		charTable[','] = Symbol.comma;
		charTable['.'] = Symbol.period;
		charTable['#'] = Symbol.neq;
		charTable[';'] = Symbol.semicolon;
		
		// ���ñ���������,������ĸ˳�򣬱����۰����
		keyword = new String[] {"begin", "call", "const", "do", "end", "if",
			"odd", "procedure", "read", "then", "var", "while", "write"};
		
		// ���ñ����ַ���
		keywordTable = new Symbol[PL0.KEYWORD_COUNT];
		keywordTable[0] = Symbol.beginsym;
		keywordTable[1] = Symbol.callsym;
		keywordTable[2] = Symbol.constsym;
		keywordTable[3] = Symbol.dosym;
		keywordTable[4] = Symbol.endsym;
		keywordTable[5] = Symbol.ifsym;
		keywordTable[6] = Symbol.oddsym;
		keywordTable[7] = Symbol.procsym;
		keywordTable[8] = Symbol.readsym;
		keywordTable[9] = Symbol.thensym;
		keywordTable[10] = Symbol.varsym;
		keywordTable[11] = Symbol.whilesym;
		keywordTable[12] = Symbol.writesym;
	}
	
	/**
	 * ��ȡһ���ַ���Ϊ���ٴ���I/O������ÿ�ζ�ȡһ��
	 */
	void getChar() {
		String l = "";
		try {
			if (charCounter == currentLineLength) {
				while (l.equals(""))
					l = inReader.readLine().toLowerCase() + "\n";
				currentLineLength = l.length();
				charCounter = 0;
				currentLine = l.toCharArray();
				System.out.println(PL0.interpreter.cx + " " + l);
				PL0.sourcePrintStream.println(PL0.interpreter.cx + " " + l);
			}
		} catch (IOException e) {
			throw new Error("program imcomplete");
		}
		justReadChar = currentLine[charCounter];
		charCounter++;
	}
	
	/**
	 * �ʷ���������ȡһ���ʷ����ţ��Ǵʷ����������ص�
	 */
	public void getSymbol() {
		// Wirth �� PL/0 ������ʹ��һϵ�е�if...else...������
		// �������������Ϊ�����д���ܹ���������ؿ�����������Ĵ����߼�
		while (Character.isWhitespace(justReadChar))		// �������пհ��ַ�
			getChar();
		if (justReadChar >= 'a' && justReadChar <= 'z') {
			// �ؼ��ֻ���һ���ʶ��
			matchKeywordOrIdentifier();
		} else if (justReadChar >= '0' && justReadChar <= '9') {
			// ����
			matchNumber();
		} else {
			// ������
			matchOperator();
		}
	}
	
	/**
	 * �����ؼ��ֻ���һ���ʶ��
	 */
	void matchKeywordOrIdentifier() {
		int i;
		StringBuilder sb = new StringBuilder(PL0.SYMBOL_MAX_LENGTH);
		// ���Ȱ��������ʶ�����
		do {
			sb.append(justReadChar);
			getChar();
		} while (justReadChar >= 'a' && justReadChar <= 'z' || justReadChar >= '0' && justReadChar <= '9');
		id = sb.toString();
		
		// Ȼ�������ǲ��Ǳ����֣���ע��ʹ�õ���ʲô����������
		i = java.util.Arrays.binarySearch(keyword, id);
		
		// ����γɷ�����Ϣ
		if (i < 0) {
			// һ���ʶ��
			currentSymbol = Symbol.ident;
		} else {
			// �ؼ���
			currentSymbol = keywordTable[i];
		}
	}
	
	/**
	 * ��������
	 */
	void matchNumber() {
		int digit = 0;
		currentSymbol = Symbol.number;
		num = 0;
		do {
			num = 10*num + Character.digit(justReadChar, 10);
			digit++;
			getChar();
		} while (justReadChar >='0' && justReadChar <='9'); 				// ��ȡ���ֵ�ֵ
		digit--;
		if (digit > PL0.MAX_NUM_DIGIT)
			Err.report(30);
	}
	
	/**
	 * ����������
	 */
	void matchOperator() {
		// ��ע�������д����Wirth���е㲻ͬ
		switch (justReadChar) {
		case ':':		// ��ֵ����
			getChar();
			if (justReadChar == '=') {
				currentSymbol = Symbol.becomes;
				getChar();
			} else {
				// ����ʶ��ķ���
				currentSymbol = Symbol.nul;
			}
			break;
		case '<':		// С�ڻ���С�ڵ���
			getChar();
			if (justReadChar == '=') {
				currentSymbol = Symbol.leq;
				getChar();
			} else {
				currentSymbol = Symbol.lss;
			}
			break;
		case '>':		// ���ڻ��ߴ��ڵ���
			getChar();
			if (justReadChar == '=') {
				currentSymbol = Symbol.geq;
				getChar();
			} else {
				currentSymbol = Symbol.gtr;
			}
			break;
		default:		// ����Ϊ���ַ���������������ŷǷ��򷵻�nil��
			currentSymbol = charTable[justReadChar];
			if (currentSymbol != Symbol.period)
				getChar();
			break;
		}
	}	
}
