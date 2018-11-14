package compiler;

/**
 * 　　语法分析器。这是PL/0分析器中最重要的部分，在语法分析的过程中穿插着语法错误检查和目标代码生成。
 */
public class Parser {
    private Scanner scanner;                    // 对词法分析器的引用
    private Table table;                    // 对符号表的引用
    private Interpreter interpreter;                // 对目标代码生成器的引用

    private final int SYMBOL_NUM = Symbol.values().length;

    // 表示声明开始的符号集合、表示语句开始的符号集合、表示因子开始的符号集合
    // 实际上这就是声明、语句和因子的FIRST集合
    private SymSet declarationBeginSet, statementBeginSet, factorBeginSet;

    /**
     * 当前符号，由nextSymbol()读入
     *
     * @see #nextSymbol()
     */
    private Symbol currentSymbol;

    /**
     * 当前作用域的堆栈帧大小，或者说数据大小（data size）
     */
    private int dataSize = 0;

    /**
     * 构造并初始化语法分析器，这里包含了C语言版本中init()函数的一部分代码
     *
     * @param l 编译器的词法分析器
     * @param t 编译器的符号表
     * @param i 编译器的目标代码生成器
     */
    public Parser(Scanner l, Table t, Interpreter i) {
        scanner = l;
        table = t;
        interpreter = i;

        // 设置声明开始符号集
        declarationBeginSet = new SymSet(SYMBOL_NUM);
        declarationBeginSet.set(Symbol.constSym);
        declarationBeginSet.set(Symbol.varSym);
        declarationBeginSet.set(Symbol.procSym);

        // 设置语句开始符号集
        statementBeginSet = new SymSet(SYMBOL_NUM);
        statementBeginSet.set(Symbol.beginSym);
        statementBeginSet.set(Symbol.callSym);
        statementBeginSet.set(Symbol.ifSym);
        statementBeginSet.set(Symbol.whileSym);
        statementBeginSet.set(Symbol.readSym);            // thanks to elu
        statementBeginSet.set(Symbol.writeSym);

        // 设置因子开始符号集
        factorBeginSet = new SymSet(SYMBOL_NUM);
        factorBeginSet.set(Symbol.ident);
        factorBeginSet.set(Symbol.number);
        factorBeginSet.set(Symbol.lparen);

    }

    /**
     * 启动语法分析过程，此前必须先调用一次nextSymbol()
     *
     * @see #nextSymbol()
     */
    public void parse() {
//        SymSet nxtlev = new SymSet(SYMBOL_NUM);
        SymSet nextLevel = new SymSet(SYMBOL_NUM);
        nextLevel.or(declarationBeginSet);
        nextLevel.or(statementBeginSet);
        nextLevel.set(Symbol.period);
        parseBlock(0, nextLevel);

        if (currentSymbol != Symbol.period)
            Err.report(9);
    }

    /**
     * 获得下一个语法符号，这里只是简单调用一下getSymbol()
     */
    public void nextSymbol() {
        scanner.getSymbol();
        currentSymbol = scanner.currentSymbol;
        isComment();
    }

    /**
     * 测试当前符号是否合法
     *
     * @param s1      我们需要的符号
     * @param s2      如果不是我们需要的，则需要一个补救用的集合
     * @param errcode 错误号
     */
    void test(SymSet s1, SymSet s2, int errcode) {
        // 在某一部分（如一条语句，一个表达式）将要结束时时我们希望下一个符号属于某集合
        //（该部分的后跟符号），test负责这项检测，并且负责当检测不通过时的补救措施，程
        // 序在需要检测时指定当前需要的符号集合和补救用的集合（如之前未完成部分的后跟符
        // 号），以及检测不通过时的错误号。
        if (!s1.get(currentSymbol)) {
            Err.report(errcode);
            // 当检测不通过时，不停获取符号，直到它属于需要的集合或补救的集合
            while (!s1.get(currentSymbol) && !s2.get(currentSymbol))
                nextSymbol();
        }
    }

    /**
     * 分析<分程序>
     *
     * @param level 当前分程序所在层
     * @param fsys  当前模块后跟符号集
     */
    public void parseBlock(int level, SymSet fsys) {
        // <分程序> := [<常量说明部分>][<变量说明部分>][<过程说明部分>]<语句>

        int dx0, tx0, cx0;                // 保留初始dx，tx和cx
        SymSet nextLevel = new SymSet(SYMBOL_NUM);

        dx0 = dataSize;                        // 记录本层之前的数据量（以便恢复）
        dataSize = 3;
        tx0 = table.tableSize;                    // 记录本层名字的初始位置（以便恢复）
        table.get(table.tableSize).adr = interpreter.cx;

        interpreter.gen(Fct.JMP, 0, 0);

        if (level > PL0.LEVEL_MAX)
            Err.report(32);

        // 分析<说明部分>
        do {
            // <常量说明部分>
            if (currentSymbol == Symbol.constSym) {
                nextSymbol();
                // the original do...while(currentSymbol == ident) is problematic, thanks to calculous
                // do
                parseConstDeclaration(level);
                while (currentSymbol == Symbol.comma) {
                    nextSymbol();
                    parseConstDeclaration(level);
                }

                if (currentSymbol == Symbol.semicolon)
                    nextSymbol();
                else
                    Err.report(5);                // 漏掉了逗号或者分号
                // } while (currentSymbol == ident);

//                isComment();
            }

            // <变量说明部分>
            if (currentSymbol == Symbol.varSym) {
                nextSymbol();
                // the original do...while(currentSymbol == ident) is problematic, thanks to calculous
                // do {
                parseVarDeclaration(level);
                while (currentSymbol == Symbol.comma) {
                    nextSymbol();
                    parseVarDeclaration(level);
                }

                if (currentSymbol == Symbol.semicolon)
                    nextSymbol();
                else
                    Err.report(5);                // 漏掉了逗号或者分号
                // } while (currentSymbol == ident);

//                isComment();
            }

////             <注释说明部分>
//            while (currentSymbol == Symbol.comment) {
//                nextSymbol();
//            }

            // <过程说明部分>
            while (currentSymbol == Symbol.procSym) {
                nextSymbol();
                if (currentSymbol == Symbol.ident) {
                    table.enter(Objekt.procedure, level, dataSize);
                    nextSymbol();
                } else {
                    Err.report(4);                // procedure后应为标识符
                }

                if (currentSymbol == Symbol.semicolon) {
                    nextSymbol();
                } else {
                    Err.report(5);                // 漏掉了分号
                }

//                isComment();

                nextLevel = (SymSet) fsys.clone();
                nextLevel.set(Symbol.semicolon);
                parseBlock(level + 1, nextLevel);

                if (currentSymbol == Symbol.semicolon) {
                    nextSymbol();

//                    isComment();

                    nextLevel = (SymSet) statementBeginSet.clone();
                    nextLevel.set(Symbol.ident);
                    nextLevel.set(Symbol.procSym);
                    test(nextLevel, fsys, 6);
                } else {
                    Err.report(5);                // 漏掉了分号
                }
            }


            nextLevel = (SymSet) statementBeginSet.clone();
            nextLevel.set(Symbol.ident);
            test(nextLevel, declarationBeginSet, 7);
        } while (declarationBeginSet.get(currentSymbol));        // 直到没有声明符号

        // 开始生成当前过程代码
        Table.Item item = table.get(tx0);
        interpreter.code[item.adr].a = interpreter.cx;
        item.adr = interpreter.cx;                    // 当前过程代码地址
        item.size = dataSize;                            // 声明部分中每增加一条声明都会给dx增加1，
        // 声明部分已经结束，dx就是当前过程的堆栈帧大小
        cx0 = interpreter.cx;
        interpreter.gen(Fct.INT, 0, dataSize);            // 生成分配内存代码

        table.debugTable(tx0);

        // 分析<语句>
        nextLevel = (SymSet) fsys.clone();        // 每个后跟符号集和都包含上层后跟符号集和，以便补救
        nextLevel.set(Symbol.semicolon);        // 语句后跟符号为分号或end
        nextLevel.set(Symbol.endSym);
        parseStatement(nextLevel, level);
        interpreter.gen(Fct.OPR, 0, 0);        // 每个过程出口都要使用的释放数据段指令

        nextLevel = new SymSet(SYMBOL_NUM);    // 分程序没有补救集合
        test(fsys, nextLevel, 8);                // 检测后跟符号正确性

        interpreter.listCode(cx0);

        dataSize = dx0;                            // 恢复堆栈帧计数器
        table.tableSize = tx0;                        // 回复名字表位置
    }

    /**
     * 分析<常量说明部分>
     *
     * @param level 当前所在的层次
     */
    void parseConstDeclaration(int level) {
        if (currentSymbol == Symbol.ident) {
            nextSymbol();
            if (currentSymbol == Symbol.equal || currentSymbol == Symbol.becomes) {
                if (currentSymbol == Symbol.becomes)
                    Err.report(1);            // 把 = 写成了 :=
                nextSymbol();
                if (currentSymbol == Symbol.number) {
                    table.enter(Objekt.constant, level, dataSize);
                    nextSymbol();
                } else {
                    Err.report(2);            // 常量说明 = 后应是数字
                }
            } else {
                Err.report(3);                // 常量说明标识后应是 =
            }
        } else {
            Err.report(4);                    // const 后应是标识符
        }
    }

    /**
     * 分析<变量说明部分>
     *
     * @param level 当前层次
     */
    void parseVarDeclaration(int level) {
        if (currentSymbol == Symbol.ident) {
            // 填写名字表并改变堆栈帧计数器
            table.enter(Objekt.variable, level, dataSize);
            dataSize++;
            nextSymbol();
        } else {
            Err.report(4);                    // var 后应是标识
        }
    }

    /**
     * 分析<语句>
     *
     * @param fsys 后跟符号集
     * @param lev  当前层次
     */
    void parseStatement(SymSet fsys, int lev) {
        SymSet nxtlev;
        // Wirth 的 PL/0 编译器使用一系列的if...else...来处理
        // 但是你的助教认为下面的写法能够更加清楚地看出这个函数的处理逻辑
        switch (currentSymbol) {
            case ident:
                parseAssignStatement(fsys, lev);
                break;
            case readSym:
                parseReadStatement(fsys, lev);
                break;
            case writeSym:
                parseWriteStatement(fsys, lev);
                break;
            case callSym:
                parseCallStatement(fsys, lev);
                break;
            case ifSym:
                parseIfStatement(fsys, lev);
                break;
            case beginSym:
                parseBeginStatement(fsys, lev);
                break;
            case whileSym:
                parseWhileStatement(fsys, lev);
                break;
            default:
                nxtlev = new SymSet(SYMBOL_NUM);
                test(fsys, nxtlev, 19);
                break;
        }
//        isComment();
    }

    /**
     * 分析<当型循环语句>
     *
     * @param fsys 后跟符号集
     * @param lev  当前层次
     */
    private void parseWhileStatement(SymSet fsys, int lev) {
        int cx1, cx2;
        SymSet nxtlev;

        cx1 = interpreter.cx;                        // 保存判断条件操作的位置
        nextSymbol();
        nxtlev = (SymSet) fsys.clone();
        nxtlev.set(Symbol.doSym);                // 后跟符号为do
        parseCondition(nxtlev, lev);            // 分析<条件>
        cx2 = interpreter.cx;                        // 保存循环体的结束的下一个位置
        interpreter.gen(Fct.JPC, 0, 0);                // 生成条件跳转，但跳出循环的地址未知
        if (currentSymbol == Symbol.doSym)
            nextSymbol();
        else
            Err.report(18);                        // 缺少do
        parseStatement(fsys, lev);                // 分析<语句>
        interpreter.gen(Fct.JMP, 0, cx1);            // 回头重新判断条件
        interpreter.code[cx2].a = interpreter.cx;            // 反填跳出循环的地址，与<条件语句>类似
    }

    /**
     * 分析<复合语句>
     *
     * @param fsys 后跟符号集
     * @param lev  当前层次
     */
    private void parseBeginStatement(SymSet fsys, int lev) {
        SymSet nxtlev;

        nextSymbol();

//        isComment();

        nxtlev = (SymSet) fsys.clone();
        nxtlev.set(Symbol.semicolon);
        nxtlev.set(Symbol.endSym);
        parseStatement(nxtlev, lev);
        // 循环分析{; <语句>}，直到下一个符号不是语句开始符号或收到end
        while (statementBeginSet.get(currentSymbol) || currentSymbol == Symbol.semicolon) {
            if (currentSymbol == Symbol.semicolon)
                nextSymbol();
            else
                Err.report(10);                    // 缺少分号
            parseStatement(nxtlev, lev);
        }
        if (currentSymbol == Symbol.endSym) {
            nextSymbol();

//            isComment();

        } else {
            Err.report(17);                        // 缺少end或分号
        }
    }

    /**
     * 分析<条件语句>
     *
     * @param fsys 后跟符号集
     * @param lev  当前层次
     */
    private void parseIfStatement(SymSet fsys, int lev) {
        int cx1;
        SymSet nxtlev;

        nextSymbol();
        nxtlev = (SymSet) fsys.clone();
        nxtlev.set(Symbol.thenSym);                // 后跟符号为then或do ???
        nxtlev.set(Symbol.doSym);
        parseCondition(nxtlev, lev);            // 分析<条件>
        if (currentSymbol == Symbol.thenSym)
            nextSymbol();
        else
            Err.report(16);                        // 缺少then
        cx1 = interpreter.cx;                        // 保存当前指令地址
        interpreter.gen(Fct.JPC, 0, 0);                // 生成条件跳转指令，跳转地址未知，暂时写0
        parseStatement(fsys, lev);                // 处理then后的语句
        interpreter.code[cx1].a = interpreter.cx;            // 经statement处理后，cx为then后语句执行
        // 完的位置，它正是前面未定的跳转地址
    }

    /**
     * 分析<过程调用语句>
     *
     * @param fsys 后跟符号集
     * @param lev  当前层次
     */
    private void parseCallStatement(SymSet fsys, int lev) {
        int i;
        nextSymbol();
        if (currentSymbol == Symbol.ident) {
            i = table.position(scanner.id);
            if (i == 0) {
                Err.report(11);                    // 过程未找到
            } else {
                Table.Item item = table.get(i);
                if (item.kind == Objekt.procedure)
                    interpreter.gen(Fct.CAL, lev - item.level, item.adr);
                else
                    Err.report(15);                // call后标识符应为过程
            }
            nextSymbol();
        } else {
            Err.report(14);                        // call后应为标识符
        }
    }

    /**
     * 分析<写语句>
     *
     * @param fsys 后跟符号集
     * @param lev  当前层次
     */
    private void parseWriteStatement(SymSet fsys, int lev) {
        SymSet nxtlev;

        nextSymbol();
        if (currentSymbol == Symbol.lparen) {
            do {
                nextSymbol();
                nxtlev = (SymSet) fsys.clone();
                nxtlev.set(Symbol.rparen);
                nxtlev.set(Symbol.comma);
                parseExpression(nxtlev, lev);
                interpreter.gen(Fct.OPR, 0, 14);
            } while (currentSymbol == Symbol.comma);

            if (currentSymbol == Symbol.rparen) {
                nextSymbol();
//                isComment();
            } else {
                Err.report(33);
            }                // write()中应为完整表达式
        }
        interpreter.gen(Fct.OPR, 0, 15);
    }

    /**
     * 分析<读语句>
     *
     * @param fsys 后跟符号集
     * @param lev  当前层次
     */
    private void parseReadStatement(SymSet fsys, int lev) {
        int i;

        nextSymbol();
        if (currentSymbol == Symbol.lparen) {
            do {
                nextSymbol();
                if (currentSymbol == Symbol.ident)
                    i = table.position(scanner.id);
                else
                    i = 0;

                if (i == 0) {
                    Err.report(35);            // read()中应是声明过的变量名
                } else {
                    Table.Item item = table.get(i);
                    if (item.kind != Objekt.variable) {
                        Err.report(32);        // read()中的标识符不是变量, thanks to amd
                    } else {
                        interpreter.gen(Fct.OPR, 0, 16);
                        interpreter.gen(Fct.STO, lev - item.level, item.adr);
                    }
                }

                nextSymbol();
            } while (currentSymbol == Symbol.comma);
        } else {
            Err.report(34);                    // 格式错误，应是左括号
        }

        if (currentSymbol == Symbol.rparen) {
            nextSymbol();

//            isComment();

        } else {
            Err.report(33);                    // 格式错误，应是右括号
            while (!fsys.get(currentSymbol))
                nextSymbol();
        }
    }

    /**
     * 分析<赋值语句>
     *
     * @param fsys 后跟符号集
     * @param lev  当前层次
     */
    private void parseAssignStatement(SymSet fsys, int lev) {
        int i;
        SymSet nxtlev;

        i = table.position(scanner.id);
        if (i > 0) {
            Table.Item item = table.get(i);
            if (item.kind == Objekt.variable) {
                nextSymbol();
                if (currentSymbol == Symbol.becomes)
                    nextSymbol();
                else
                    Err.report(13);                    // 没有检测到赋值符号
                nxtlev = (SymSet) fsys.clone();
                parseExpression(nxtlev, lev);
                // parseExpression将产生一系列指令，但最终结果将会保存在栈顶，执行sto命令完成赋值
                interpreter.gen(Fct.STO, lev - item.level, item.adr);
            } else {
                Err.report(12);                        // 赋值语句格式错误
            }
        } else {
            Err.report(11);                            // 变量未找到
        }
    }

    /**
     * 分析<表达式>
     *
     * @param fsys 后跟符号集
     * @param lev  当前层次
     */
    private void parseExpression(SymSet fsys, int lev) {
        Symbol addop;
        SymSet nxtlev;

        // 分析[+|-]<项>
        if (currentSymbol == Symbol.plus || currentSymbol == Symbol.minus) {
            addop = currentSymbol;
            nextSymbol();
            nxtlev = (SymSet) fsys.clone();
            nxtlev.set(Symbol.plus);
            nxtlev.set(Symbol.minus);
            parseTerm(nxtlev, lev);
            if (addop == Symbol.minus)
                interpreter.gen(Fct.OPR, 0, 1);
        } else {
            nxtlev = (SymSet) fsys.clone();
            nxtlev.set(Symbol.plus);
            nxtlev.set(Symbol.minus);
            parseTerm(nxtlev, lev);
        }

        // 分析{<加法运算符><项>}
        while (currentSymbol == Symbol.plus || currentSymbol == Symbol.minus) {
            addop = currentSymbol;
            nextSymbol();
            nxtlev = (SymSet) fsys.clone();
            nxtlev.set(Symbol.plus);
            nxtlev.set(Symbol.minus);
            parseTerm(nxtlev, lev);
            if (addop == Symbol.plus)
                interpreter.gen(Fct.OPR, 0, 2);
            else
                interpreter.gen(Fct.OPR, 0, 3);
        }
    }

    /**
     * 分析<项>
     *
     * @param fsys 后跟符号集
     * @param lev  当前层次
     */
    private void parseTerm(SymSet fsys, int lev) {
        Symbol mulop;
        SymSet nxtlev;

        // 分析<因子>
        nxtlev = (SymSet) fsys.clone();
        nxtlev.set(Symbol.times);
        nxtlev.set(Symbol.slash);
        parseFactor(nxtlev, lev);

        // 分析{<乘法运算符><因子>}
        while (currentSymbol == Symbol.times || currentSymbol == Symbol.slash) {
            mulop = currentSymbol;
            nextSymbol();
            parseFactor(nxtlev, lev);
            if (mulop == Symbol.times)
                interpreter.gen(Fct.OPR, 0, 4);
            else
                interpreter.gen(Fct.OPR, 0, 5);
        }
    }

    /**
     * 分析<因子>
     *
     * @param fsys 后跟符号集
     * @param lev  当前层次
     */
    private void parseFactor(SymSet fsys, int lev) {
        SymSet nxtlev;

        test(factorBeginSet, fsys, 24);            // 检测因子的开始符号
        // the original while... is problematic: var1(var2+var3)
        // thanks to macross
        // while(inset(currentSymbol, factorBeginSet))
        if (factorBeginSet.get(currentSymbol)) {
            if (currentSymbol == Symbol.ident) {            // 因子为常量或变量
                int i = table.position(scanner.id);
                if (i > 0) {
                    Table.Item item = table.get(i);
                    switch (item.kind) {
                        case constant:            // 名字为常量
                            interpreter.gen(Fct.LIT, 0, item.val);
                            break;
                        case variable:            // 名字为变量
                            interpreter.gen(Fct.LOD, lev - item.level, item.adr);
                            break;
                        case procedure:            // 名字为过程
                            Err.report(21);                // 不能为过程
                            break;
                    }
                } else {
                    Err.report(11);                    // 标识符未声明
                }
                nextSymbol();
            } else if (currentSymbol == Symbol.number) {    // 因子为数
                int num = scanner.num;
                if (num > PL0.MAX_NUM) {
                    Err.report(31);//超过数值范围
                    num = 0;
                }
                interpreter.gen(Fct.LIT, 0, num);
                nextSymbol();
            } else if (currentSymbol == Symbol.lparen) {    // 因子为表达式
                nextSymbol();
                nxtlev = (SymSet) fsys.clone();
                nxtlev.set(Symbol.rparen);
                parseExpression(nxtlev, lev);
                if (currentSymbol == Symbol.rparen)
                    nextSymbol();
                else
                    Err.report(22);                    // 缺少右括号
            } else {
                // 做补救措施
                test(fsys, factorBeginSet, 23);
            }
        }
    }

    /**
     * 分析<条件>
     *
     * @param fsys 后跟符号集
     * @param lev  当前层次
     */
    private void parseCondition(SymSet fsys, int lev) {
        Symbol relop;
        SymSet nxtlev;

        if (currentSymbol == Symbol.oddSym) {
            // 分析 ODD<表达式>
            nextSymbol();
            parseExpression(fsys, lev);
            interpreter.gen(Fct.OPR, 0, 6);
        } else {
            // 分析<表达式><关系运算符><表达式>
            nxtlev = (SymSet) fsys.clone();
            nxtlev.set(Symbol.equal);
            nxtlev.set(Symbol.neq);
            nxtlev.set(Symbol.lss);
            nxtlev.set(Symbol.leq);
            nxtlev.set(Symbol.gtr);
            nxtlev.set(Symbol.geq);
            parseExpression(nxtlev, lev);
            if (currentSymbol == Symbol.equal || currentSymbol == Symbol.neq
                    || currentSymbol == Symbol.lss || currentSymbol == Symbol.leq
                    || currentSymbol == Symbol.gtr || currentSymbol == Symbol.geq) {
                relop = currentSymbol;
                nextSymbol();
                parseExpression(fsys, lev);
                switch (relop) {
                    case equal:
                        interpreter.gen(Fct.OPR, 0, 8);
                        break;
                    case neq:
                        interpreter.gen(Fct.OPR, 0, 9);
                        break;
                    case lss:
                        interpreter.gen(Fct.OPR, 0, 10);
                        break;
                    case geq:
                        interpreter.gen(Fct.OPR, 0, 11);
                        break;
                    case gtr:
                        interpreter.gen(Fct.OPR, 0, 12);
                        break;
                    case leq:
                        interpreter.gen(Fct.OPR, 0, 13);
                        break;
                }
            } else {
                Err.report(20);
            }
        }
    }

    /**
     * 跳过注释
     * @author: KanModel
     */
    void isComment() {
        while (currentSymbol == Symbol.comment) {
            nextSymbol();
        }
    }
}
