package compiler;

/**
 *���������ֻ�ǰ����˱������Լ������������
 */
public class Err {
	/**
	 * ��������������������һ���ж��ٸ�����
	 */
	public static int err = 0;
	
	/**
	 * ������
	 * @param errCode ������
	 */
	public static void report(int errCode) {
		char[] s = new char[PL0.scanner.charCounter -1];
		java.util.Arrays.fill(s, ' ');
		String space = new String(s);
		System.out.println("****" + space + "!" + errCode);
		PL0.sourcePrintStream.println("****" + space + "!" + errCode);
		err ++;
	}
}
