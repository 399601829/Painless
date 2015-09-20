package painless;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import static painless.PainlessExternal.*;
import static painless.PainlessParser.*;
import static painless.PainlessTypes.*;

class PainlessAnalyzer extends PainlessBaseVisitor<Void> {
    static class PArgument {
        private final String pname;
        private final PType ptype;

        PArgument(final String pname, final PType ptype) {
            this.pname = pname;
            this.ptype = ptype;
        }

        String getPName() {
            return pname;
        }

        PType getPType() {
            return ptype;
        }
    }

    static class PVariable {
        private final String pname;
        private final PType ptype;
        private final int aslot;

        PVariable(final String pname, final PType ptype, final int aslot) {
            this.pname = pname;
            this.ptype = ptype;
            this.aslot = aslot;
        }

        String getPName() {
            return pname;
        }

        PType getPType() {
            return ptype;
        }

        int getASlot() {
            return aslot;
        }
    }

    static class PMetadata {
        final ParseTree node;

        boolean rtn;
        boolean jump;
        boolean statement;
        boolean conditional;

        boolean righthand;
        PType pdecltype;

        ParseTree castnodes[];
        PType castptypes[];

        Object constant;
        PExternal pexternal;

        PCast pcast;

        PMetadata(final ParseTree node) {
            this.node = node;

            rtn = false;
            jump = false;
            statement = false;
            conditional = false;

            righthand = false;
            pdecltype = null;

            castnodes = null;
            castptypes = null;

            constant = null;
            pexternal = null;

            pcast = null;
        }
    }

    static Map<ParseTree, PMetadata> analyze(
            final PTypes ptypes, final ParseTree root, final Deque<PArgument> arguments) {
        return new PainlessAnalyzer(ptypes, root, arguments).pmetadata;
    }

    private final PTypes ptypes;

    private int loop;
    private final Deque<Integer> ascopes;
    private final Deque<PVariable> pvariables;

    private final Map<ParseTree, PMetadata> pmetadata;

    private PainlessAnalyzer(final PTypes ptypes, final ParseTree root, final Deque<PArgument> parguments) {
        this.ptypes = ptypes;

        loop = 0;
        pvariables = new ArrayDeque<>();
        ascopes = new ArrayDeque<>();

        pmetadata = new HashMap<>();

        incrementScope();

        for (final PArgument argument : parguments) {
            addVariable(argument.pname, argument.ptype);
        }

        createMetadata(root);
        visit(root);

        decrementScope();
    }

    private void incrementScope() {
        ascopes.push(0);
    }

    private void decrementScope() {
        int remove = ascopes.pop();

        while (remove > 0) {
            pvariables.pop();
            --remove;
        }
    }

    private PVariable getVariable(final String name) {
        final Iterator<PVariable> itr = pvariables.descendingIterator();

        while (itr.hasNext()) {
            final PVariable variable = itr.next();

            if (variable.pname.equals(name)) {
                return variable;
            }
        }

        return null;
    }

    private void addVariable(final String name, final PType ptype) {
        if (getVariable(name) != null) {
            throw new IllegalArgumentException();
        }

        final PVariable previous = pvariables.peek();
        int aslot = 0;

        if (previous != null) {
            aslot += previous.ptype.getPSort().getASize();
        }

        final PVariable pvariable = new PVariable(name, ptype, aslot);
        pvariables.push(pvariable);

        final int update = ascopes.pop() + 1;
        ascopes.push(update);
    }

    private PMetadata createMetadata(final ParseTree node) {
        final PMetadata nodemd = new PMetadata(node);
        pmetadata.put(node, nodemd);

        return nodemd;
    }

    private PMetadata getMetadata(ParseTree node) {
        final PMetadata nodemd = pmetadata.get(node);

        if (nodemd == null) {
            throw new IllegalStateException(); // TODO: message
        }

        return nodemd;
    }

    private boolean isImplicitTransform(PType pfrom, PType pto) {
        final PCast pcast = new PCast(pfrom, pto);

        if (ptypes.isPDisallowed(pcast)) {
            throw new IllegalArgumentException(); // TODO: message
        }

        if (ptypes.getPExplicit(pcast) != null) {
            throw new IllegalArgumentException(); // TODO: message
        }

        return ptypes.getPImplicit(pcast) != null;
    }

    private boolean isType(final PType ptypes[], final PType ptype) {
        for (PType plocal : ptypes) {
           if (!ptype.equals(plocal)) {
                return false;
           }
        }

        return true;
    }

    private boolean isType(final PType ptypes[], final PSort psort) {
        for (PType ptype : ptypes) {
            if (!ptype.getPSort().equals(psort)) {
                return false;
            }
        }

        return true;
    }

    private boolean isNumeric(final PType ptypes[]) {
        /*for (PType ptype : ptypes) {
            if (this.ptypes.getPImplicit(ptype))
        }

        return ptypes.length > 0;*/

        return false;
    }

    private boolean isLegalCast(final PMetadata metadata, final PType pto, final boolean explicit) {
        //TODO: check legality of cast
        return false;
    }

    private PType getPromotion(final PMetadata[] metadatas, final boolean decimal) {
        /*Type apromote = INT_TYPE;

        for (final Metadata metadata : metadatas) {
            if (!isNumeric(metadata.castatypes)) {
                throw new IllegalArgumentException();
            }

            for (final Type atype : metadata.castatypes) {
                if (atype.getSort() == LONG && apromote.getSort() == INT) {
                    apromote = LONG_TYPE;
                } else if (atype.getSort() == FLOAT && apromote.getSort() != DOUBLE) {
                    if (!decimal) {
                        throw new IllegalArgumentException();
                    }

                    apromote = FLOAT_TYPE;
                } else if (atype.getSort() == DOUBLE) {
                    if (!decimal) {
                        throw new IllegalArgumentException();
                    }

                    apromote = DOUBLE_TYPE;
                }
            }
        }

        return apromote;*/

        return null;
    }

    private Number getNumericFromChar(final char character, final PType pnumeric) {
        /*final int asort = anumeric.getSort()

        if (asort == BYTE) {
            return new Byte((byte)character);
        } else if (asort == SHORT) {
            return new Short((short)character);
        } else if (asort == INTEGER) {
            return new Integer(character);
        } else if (asort == LONG) {
            return new Long(character);
        } else if (asort == FLOAT) {
            return new Float(character);
        } else if (asort == DOUBLE) {
            return new Double(character);
        }

        throw new IllegalArgumentException();*/

        return null;
    }

    @Override
    public Void visitSource(final SourceContext ctx) {
        final PMetadata sourcemd = pmetadata.get(ctx);

        incrementScope();

        for (final StatementContext sctx : ctx.statement()) {
            if (sourcemd.rtn) {
                throw new IllegalStateException();
            }

            final PMetadata statementmd = createMetadata(sctx);
            visit(sctx);

            if (!statementmd.statement) {
                throw new IllegalStateException(); // TODO: message
            }

            sourcemd.rtn = statementmd.rtn;
        }

        sourcemd.statement = true;

        decrementScope();

        return null;
    }

    @Override
    public Void visitIf(IfContext ctx) {
        final PMetadata ifmd = getMetadata(ctx);

        incrementScope();

        final ExpressionContext ectx = ctx.expression();
        final PMetadata expressionmd = createMetadata(ectx);
        expressionmd.righthand = true;
        visit(ectx);
        // TODO: cast

        final BlockContext bctx0 = ctx.block(0);
        final PMetadata blockmd0 = createMetadata(bctx0);
        visit(ctx.block(0));

        if (ctx.ELSE() != null) {
            final BlockContext bctx1 = ctx.block(1);
            final PMetadata blockmd1 = createMetadata(bctx1);
            visit(ctx.block(1));
            ifmd.rtn = blockmd0.rtn && blockmd1.rtn;
        }

        ifmd.statement = true;

        decrementScope();

        return null;
    }

    @Override
    public Void visitWhile(final WhileContext ctx) {
        final PMetadata whilemd = getMetadata(ctx);

        incrementScope();
        ++loop;

        final ExpressionContext ectx = ctx.expression();
        final PMetadata expressionmd = createMetadata(ectx);
        expressionmd.righthand = true;
        visit(ectx);
        // TODO: cast

        final BlockContext bctx = ctx.block();
        createMetadata(bctx);
        visit(bctx);

        whilemd.statement = true;

        --loop;
        decrementScope();

        return null;
    }

    @Override
    public Void visitDo(final DoContext ctx) {
        final PMetadata domd = getMetadata(ctx);

        incrementScope();
        ++loop;

        final BlockContext bctx = ctx.block();
        createMetadata(bctx);
        visit(bctx);

        final ExpressionContext ectx = ctx.expression();
        final PMetadata expressionmd = createMetadata(ectx);
        expressionmd.righthand = true;
        visit(ectx);
        // TODO: cast

        domd.statement = true;

        --loop;
        decrementScope();

        return null;
    }

    @Override
    public Void visitFor(final ForContext ctx) {
        final PMetadata formd = getMetadata(ctx);

        incrementScope();
        ++loop;

        final DeclarationContext dctx = ctx.declaration();
        final PMetadata declarationmd = createMetadata(dctx);
        visit(dctx);

        if (!declarationmd.statement) {
            throw new IllegalStateException(); // TODO: message
        }

        final ExpressionContext ectx0 = ctx.expression(0);
        final PMetadata expressionmd0 = createMetadata(ectx0);
        expressionmd0.righthand = true;
        visit(ectx0);
        // TODO: cast

        final ExpressionContext ectx1 = ctx.expression(0);
        final PMetadata expressionmd1 = createMetadata(ectx1);
        visit(ectx1);

        if (!expressionmd1.statement) {
            throw new IllegalStateException(); // TODO: message
        }

        final BlockContext bctx = ctx.block();
        createMetadata(bctx);
        visit(bctx);

        formd.statement = true;

        --loop;
        decrementScope();

        return null;
    }

    @Override
    public Void visitDecl(final DeclContext ctx) {
        final PMetadata declmd = getMetadata(ctx);

        final DeclarationContext dctx = ctx.declaration();
        final PMetadata declarationmd = createMetadata(dctx);
        visit(ctx.declaration());

        declmd.statement = declarationmd.statement;

        return null;
    }

    @Override
    public Void visitContinue(final ContinueContext ctx) {
        final PMetadata continuemd = getMetadata(ctx);

        if (loop == 0) {
            throw new IllegalStateException(); // TODO: message
        }

        continuemd.jump = true;
        continuemd.statement = true;

        return null;
    }

    @Override
    public Void visitBreak(final BreakContext ctx) {
        final PMetadata breakmd = getMetadata(ctx);

        if (loop == 0) {
            throw new IllegalStateException();
        }

        breakmd.jump = true;
        breakmd.statement = true;

        return null;
    }

    @Override
    public Void visitReturn(final ReturnContext ctx) {
        final PMetadata returnmd = getMetadata(ctx);

        final ExpressionContext ectx = ctx.expression();
        final PMetadata expressionmd = createMetadata(ectx);
        expressionmd.righthand = true;
        visit(ectx);
        // TODO: cast

        returnmd.rtn = true;
        returnmd.statement = true;

        return null;
    }

    @Override
    public Void visitExpr(final ExprContext ctx) {
        final PMetadata exprmd = getMetadata(ctx);

        final ExpressionContext ectx = ctx.expression();
        final PMetadata expressionmd = createMetadata(ectx);
        visit(ectx);

        exprmd.statement = expressionmd.statement;

        return null;
    }

    @Override
    public Void visitMultiple(final MultipleContext ctx) {
        final PMetadata multiplemd = getMetadata(ctx);

        for (StatementContext sctx : ctx.statement()) {
            if (multiplemd.rtn || multiplemd.jump) {
                throw new IllegalStateException();
            }

            final PMetadata statementmd = new PMetadata(sctx);
            visit(sctx);

            if (!statementmd.statement) {
                throw new IllegalStateException(); // TODO: message
            }

            multiplemd.rtn = statementmd.rtn;
            multiplemd.jump = statementmd.jump;
        }

        multiplemd.statement = true;

        return null;
    }

    @Override
    public Void visitSingle(final SingleContext ctx) {
        final PMetadata singlemd = getMetadata(ctx);

        final StatementContext sctx = ctx.statement();
        final PMetadata statementmd = new PMetadata(sctx);
        visit(sctx);

        if (!statementmd.statement) {
            throw new IllegalStateException(); // TODO: message
        }

        singlemd.statement = true;

        return null;
    }

    @Override
    public Void visitEmpty(final EmptyContext ctx) {
        final PMetadata emptymd = getMetadata(ctx);
        emptymd.statement = true;

        return null;
    }

    @Override
    public Void visitDeclaration(final DeclarationContext ctx) {
        final PMetadata declarationmd = getMetadata(ctx);

        final DecltypeContext dctx = ctx.decltype();
        final PMetadata decltypemd = createMetadata(dctx);
        visit(dctx);
        final PType pdecltype = decltypemd.pdecltype;

        if (pdecltype == null) {
            throw new IllegalArgumentException(); // TODO: message
        }

        for (int child = 0; child < ctx.getChildCount(); ++child) {
            final ParseTree cctx = ctx.getChild(child);

            if (cctx instanceof TerminalNode) {
                final TerminalNode tctx = (TerminalNode)cctx;

                if (tctx.getSymbol().getType() == PainlessLexer.ID) {
                    final String name = tctx.getText();
                    addVariable(name, pdecltype);
                }
            } else if (cctx instanceof ExpressionContext) {
                final ExpressionContext ectx = (ExpressionContext)cctx;
                final PMetadata expressiondmd = createMetadata(ectx);
                expressiondmd.righthand = true;
                visit(ectx);
                // TODO: cast
            }
        }

        declarationmd.statement = true;

        return null;
    }

    @Override
    public Void visitDecltype(final DecltypeContext ctx) {
        final PMetadata decltypemd = getMetadata(ctx);

        final String pnamestr = ctx.getText();
        decltypemd.pdecltype = getPTypeFromCanonicalPName(ptypes, pnamestr);

        return null;
    }

    @Override
    public Void visitPrecedence(final PrecedenceContext ctx) {
        final PMetadata precedencemd = getMetadata(ctx);

        final ExpressionContext ectx = ctx.expression();
        final PMetadata expressionmd = createMetadata(ectx);
        expressionmd.righthand = precedencemd.righthand;
        visit(ectx);

        precedencemd.castnodes = expressionmd.castnodes;
        precedencemd.castptypes = expressionmd.castptypes;
        precedencemd.constant = expressionmd.constant;

        return null;
    }

    @Override
    public Void visitNumeric(final NumericContext ctx) {
        final PMetadata numericmd = getMetadata(ctx);

        numericmd.castnodes = new ParseTree[] {ctx};
        numericmd.castptypes = new PType[1];

        if (ctx.DECIMAL() != null) {
            final String svalue = ctx.DECIMAL().getText();

            if (svalue.endsWith("f") || svalue.endsWith("F")) {
                try {
                    numericmd.constant = Float.parseFloat(svalue);
                    numericmd.castptypes[0] = ptypes.getPSortPType(PSort.FLOAT);
                } catch (NumberFormatException exception) {
                    throw new IllegalArgumentException(); // TODO: message
                }
            } else {
                try {
                    numericmd.constant = Double.parseDouble(svalue);
                    numericmd.castptypes[0] = ptypes.getPSortPType(PSort.DOUBLE);
                } catch (NumberFormatException exception) {
                    throw new IllegalArgumentException(); // TODO: message
                }
            }
        } else {
            String svalue;
            int radix;

            if (ctx.OCTAL() != null) {
                svalue = ctx.OCTAL().getText();
                radix = 8;
            } else if (ctx.INTEGER() != null) {
                svalue = ctx.INTEGER().getText();
                radix = 10;
            } else if (ctx.HEX() != null) {
                svalue = ctx.HEX().getText();
                radix = 16;
            } else {
                throw new IllegalStateException(); // TODO: message
            }

            if (svalue.endsWith("l") || svalue.endsWith("L")) {
                try {
                    numericmd.constant = Long.parseLong(svalue, radix);
                    numericmd.castptypes[0] = ptypes.getPSortPType(PSort.LONG);
                } catch (NumberFormatException exception) {
                    throw new IllegalArgumentException(); // TODO: message
                }
            } else {
                try {
                    numericmd.constant = Integer.parseInt(svalue, radix);
                    numericmd.castptypes[0] = ptypes.getPSortPType(PSort.INT);
                } catch (NumberFormatException exception) {
                    throw new IllegalArgumentException(); // TODO: message
                }
            }
        }

        return null;
    }

    @Override
    public Void visitString(final StringContext ctx) {
        final PMetadata stringmd = getMetadata(ctx);

        if (ctx.STRING() == null) {
            throw new IllegalStateException(); // TODO: message
        }

        stringmd.constant = ctx.STRING().getText();
        stringmd.castnodes = new ParseTree[] {ctx};
        stringmd.castptypes = new PType[] {ptypes.getPSortPType(PSort.STRING)};

        return null;
    }

    @Override
    public Void visitChar(final CharContext ctx) {
        final PMetadata charmd = getMetadata(ctx);

        if (ctx.CHAR() == null) {
            throw new IllegalStateException(); // TODO: message
        }

        if (ctx.CHAR().getText().length() > 1) {
            throw new IllegalStateException(); // TODO: message
        }

        charmd.constant = ctx.CHAR().getText().charAt(0);
        charmd.castnodes = new ParseTree[] {ctx};
        charmd.castptypes = new PType[] {ptypes.getPSortPType(PSort.CHAR)};

        return null;
    }
    @Override
    public Void visitTrue(final TrueContext ctx) {
        final PMetadata truemd = getMetadata(ctx);

        if (ctx.TRUE() == null) {
            throw new IllegalStateException(); // TODO: message
        }

        truemd.constant = true;
        truemd.castnodes = new ParseTree[] {ctx};
        truemd.castptypes = new PType[] {ptypes.getPSortPType(PSort.BOOL)};

        return null;
    }

    @Override
    public Void visitFalse(final FalseContext ctx) {
        final PMetadata falsemd = getMetadata(ctx);

        if (ctx.FALSE() == null) {
            throw new IllegalStateException(); // TODO: message
        }

        falsemd.constant = false;
        falsemd.castnodes = new ParseTree[] {ctx};
        falsemd.castptypes = new PType[] {ptypes.getPSortPType(PSort.BOOL)};

        return null;
    }

    @Override
    public Void visitNull(final NullContext ctx) {
        final PMetadata nullmd = getMetadata(ctx);

        if (ctx.NULL() == null) {
            throw new IllegalStateException(); // TODO: message
        }

        nullmd.castnodes = new ParseTree[] {ctx};
        nullmd.castptypes = new PType[] {ptypes.getPSortPType(PSort.OBJECT)};

        return null;
    }

    @Override
    public Void visitExt(final ExtContext ctx) {
        final PMetadata extmd = getMetadata(ctx);

        final ExtstartContext ectx = ctx.extstart();
        final PMetadata extstartmd = createMetadata(ectx);
        visit(ectx);

        extmd.statement = extstartmd.statement;
        extmd.castnodes = extstartmd.castnodes;
        extmd.castptypes = extstartmd.castptypes;

        return null;
    }

    /*@Override
    public Void visitUnary(final UnaryContext ctx) {
        final PMetadata unarymd = getMetadata(ctx);
        unarymd.castnodes = new ParseTree[] {ctx};
        unarymd.castptypes = new PType[1];

        final ExpressionContext ectx = ctx.expression();
        final PMetadata expressionmd = createMetadata(ectx);
        visit(ectx);

        if (ctx.BOOLNOT() != null) {
            if (expressionmd.constant instanceof Boolean) {
                unarymd.constant = !((Boolean)expressionmd.constant);
            } else {
                // TODO: cast
            }

            unarymd.castptypes[0] = getPTypeFromCanonicalPName(ptypes, "bool"); // TODO: map
        } else if (ctx.BWNOT() != null) {
            if (expressionmd.constant instanceof Byte) {
                if (isImplicitTransform(getPTypeFromCanonicalPName(ptypes, "")))
                unarymd.constant = ~((byte)expressionmd.constant);
                unarymd.castatypes[0] = INT_TYPE;
            } else if (expressionmd.constant instanceof Character) {
                unarymd.constant = ~((char)expressionmd.constant);
                unarymd.castatypes[0] = INT_TYPE;
            } else if (expressionmd.constant instanceof Short) {
                unarymd.constant = ~((short)expressionmd.constant);
                unarymd.castatypes[0] = INT_TYPE;
            } else if (expressionmd.constant instanceof Integer) {
                unarymd.constant = ~((int)expressionmd.constant);
                unarymd.castatypes[0] = INT_TYPE;
            } else if (expressionmd.constant instanceof Long) {
                unarymd.constant = ~((long)expressionmd.constant);
                unarymd.castatypes[0] = LONG_TYPE;
            } else if (expressionmd.constant instanceof Float) {
                throw new IllegalArgumentException();
            } else if (expressionmd.constant instanceof Double) {
                throw new IllegalArgumentException();
            } else {
                unarymd.castatypes[0] = getPromotion(new Metadata[] {expressionmd}, false);
                markCast(expressionmd, unarymd.castatypes[0], false);
            }
        } else if (ctx.SUB() != null) {
            if (expressionmd.constant instanceof Byte) {
                unarymd.constant = -((byte)expressionmd.constant);
                unarymd.castatypes[0] = INT_TYPE;
            } else if (expressionmd.constant instanceof Character) {
                unarymd.constant = -((char)expressionmd.constant);
                unarymd.castatypes[0] = INT_TYPE;
            } else if (expressionmd.constant instanceof Short) {
                unarymd.constant = -((short)expressionmd.constant);
                unarymd.castatypes[0] = INT_TYPE;
            } else if (expressionmd.constant instanceof Integer) {
                unarymd.constant = -((int)expressionmd.constant);
                unarymd.castatypes[0] = INT_TYPE;
            } else if (expressionmd.constant instanceof Long) {
                unarymd.constant = -((long)expressionmd.constant);
                unarymd.castatypes[0] = LONG_TYPE;
            } else if (expressionmd.constant instanceof Float) {
                unarymd.constant = -((float)expressionmd.constant);
                unarymd.castatypes[0] = FLOAT_TYPE;
            } else if (expressionmd.constant instanceof Double) {
                unarymd.constant = -((double)expressionmd.constant);
                unarymd.castatypes[0] = DOUBLE_TYPE;
            } else {
                unarymd.castatypes[0] = getPromotion(new Metadata[] {expressionmd}, true);
                markCast(expressionmd, unarymd.castatypes[0], false);
            }
        } else if (ctx.ADD() != null) {
            if (expressionmd.constant instanceof Byte) {
                unarymd.constant = +((byte)expressionmd.constant);
                unarymd.castatypes[0] = INT_TYPE;
            } else if (expressionmd.constant instanceof Character) {
                unarymd.constant = +((char)expressionmd.constant);
                unarymd.castatypes[0] = INT_TYPE;
            } else if (expressionmd.constant instanceof Short) {
                unarymd.constant = +((short)expressionmd.constant);
                unarymd.castatypes[0] = INT_TYPE;
            } else if (expressionmd.constant instanceof Integer) {
                unarymd.constant = +((int)expressionmd.constant);
                unarymd.castatypes[0] = INT_TYPE;
            } else if (expressionmd.constant instanceof Long) {
                unarymd.constant = +((long)expressionmd.constant);
                unarymd.castatypes[0] = LONG_TYPE;
            } else if (expressionmd.constant instanceof Float) {
                unarymd.constant = +((float)expressionmd.constant);
                unarymd.castatypes[0] = FLOAT_TYPE;
            } else if (expressionmd.constant instanceof Double) {
                unarymd.constant = +((double)expressionmd.constant);
                unarymd.castatypes[0] = DOUBLE_TYPE;
            } else {
                unarymd.castatypes[0] = getPromotion(new Metadata[] {expressionmd}, true);
                markCast(expressionmd, unarymd.castatypes[0], false);
            }
        } else {
            throw new IllegalStateException();
        }

        return null;
    }

    @Override
    public Void visitCast(final CastContext ctx) {
        final Metadata castmd = getMetadata(ctx);
        castmd.castnodes = new ParseTree[] {ctx};
        castmd.castatypes = new Type[1];

        final DecltypeContext dctx = ctx.decltype();
        final Metadata decltypemd = createMetadata(dctx);
        final Type adecltype = decltypemd.adecltype;
        castmd.castatypes = new Type[] {adecltype};

        final ExpressionContext ectx = ctx.expression();
        final Metadata expressionmd = createMetadata(ectx);
        expressionmd.righthand = castmd.righthand;
        visit(ectx);

        if (expressionmd.constant instanceof Number) {
            final int adeclsort = adecltype.getSort();

            if (adeclsort == BYTE) {
                castmd.constant = ((Number)expressionmd.constant).byteValue();
            } else if (adeclsort == SHORT) {
                castmd.constant = ((Number)expressionmd.constant).shortValue();
            } else if (adeclsort == Type.CHAR) {
                castmd.constant = (char)((Number)expressionmd.constant).longValue();
            }else if (adeclsort == INT) {
                castmd.constant = ((Number)expressionmd.constant).intValue();
            } else if (adeclsort == LONG) {
                castmd.constant = ((Number)expressionmd.constant).longValue();
            } else if (adeclsort == FLOAT) {
                castmd.constant = ((Number)expressionmd.constant).floatValue();
            } else if (adeclsort == DOUBLE) {
                castmd.constant = ((Number)expressionmd.constant).doubleValue();
            }
        } else if (expressionmd.constant instanceof Character) {
            final int adeclsort = adecltype.getSort();

            if (adeclsort == BYTE) {
                castmd.constant = (byte)(char)expressionmd.constant;
            } else if (adeclsort == SHORT) {
                castmd.constant = (short)(char)expressionmd.constant;
            } else if (adeclsort == Type.CHAR) {
                castmd.constant = expressionmd.constant;
            }else if (adeclsort == INT) {
                castmd.constant = (int)(char)expressionmd.constant;
            } else if (adeclsort == LONG) {
                castmd.constant = (long)(char)expressionmd.constant;
            } else if (adeclsort == FLOAT) {
                castmd.constant = (float)(char)expressionmd.constant;
            } else if (adeclsort == DOUBLE) {
                castmd.constant = (double)(char)expressionmd.constant;
            }
        } else {
            markCast(expressionmd, adecltype, true);
        }

        return null;
    }

    @Override
    public Void visitBinary(final BinaryContext ctx) {
        final Metadata binarymd = getMetadata(ctx);
        binarymd.castnodes = new ParseTree[] {ctx};
        binarymd.castatypes = new Type[1];

        final ExpressionContext ectx0 = ctx.expression(0);
        final Metadata expressionmd0 = createMetadata(ectx0);
        visit(ectx0);

        final ExpressionContext ectx1 = ctx.expression(1);
        final Metadata expressionmd1 = createMetadata(ectx1);
        visit(ectx1);

        final boolean decimal = ctx.ADD() != null || ctx.SUB() != null ||
                ctx.DIV() != null || ctx.MUL() != null || ctx.REM() != null;
        binarymd.castatypes[0] = getPromotion(new Metadata[] {expressionmd0, expressionmd1}, decimal);
        final int asort = binarymd.castatypes[0].getSort();
        Number number0 = null;
        Number number1 = null;

        if (expressionmd0.constant instanceof Number) {
            number0 = (Number)expressionmd0.constant;
        } else if (expressionmd0.constant instanceof Character) {
            number0 = getNFromC((char)expressionmd0.constant, binarymd.castatypes[0]);
        }

        if (expressionmd1.constant instanceof Number) {
            number1 = (Number)expressionmd1.constant;
        } else if (expressionmd0.constant instanceof Character) {
            number1 = getNFromC((char)expressionmd1.constant, binarymd.castatypes[0]);
        }

        if (number0 != null && number1 != null) {
            if (ctx.MUL() != null) {
                if (asort == INT) {
                    binarymd.constant = number0.intValue() * number1.intValue();
                } else if (asort == LONG) {
                    binarymd.constant = number0.longValue() * number1.longValue();
                } else if (asort == FLOAT) {
                    binarymd.constant = number0.floatValue() * number1.floatValue();
                } else if (asort == DOUBLE) {
                    binarymd.constant = number0.doubleValue() * number1.doubleValue();
                }
            } else if (ctx.DIV() != null) {
                if (asort == INT) {
                    binarymd.constant = number0.intValue() / number1.intValue();
                } else if (asort == LONG) {
                    binarymd.constant = number0.longValue() / number1.longValue();
                } else if (asort == FLOAT) {
                    binarymd.constant = number0.floatValue() / number1.floatValue();
                } else if (asort == DOUBLE) {
                    binarymd.constant = number0.doubleValue() / number1.doubleValue();
                }
            } else if (ctx.REM() != null) {
                if (asort == INT) {
                    binarymd.constant = number0.intValue() % number1.intValue();
                } else if (asort == LONG) {
                    binarymd.constant = number0.longValue() % number1.longValue();
                } else if (asort == FLOAT) {
                    binarymd.constant = number0.floatValue() % number1.floatValue();
                } else if (asort == DOUBLE) {
                    binarymd.constant = number0.doubleValue() % number1.doubleValue();
                }
            } else if (ctx.ADD() != null) {
                if (asort == INT) {
                    binarymd.constant = number0.intValue() + number1.intValue();
                } else if (asort == LONG) {
                    binarymd.constant = number0.longValue() + number1.longValue();
                } else if (asort == FLOAT) {
                    binarymd.constant = number0.floatValue() + number1.floatValue();
                } else if (asort == DOUBLE) {
                    binarymd.constant = number0.doubleValue() + number1.doubleValue();
                }
            }  else if (ctx.SUB() != null) {
                if (asort == INT) {
                    binarymd.constant = number0.intValue() - number1.intValue();
                } else if (asort == LONG) {
                    binarymd.constant = number0.longValue() - number1.longValue();
                } else if (asort == FLOAT) {
                    binarymd.constant = number0.floatValue() - number1.floatValue();
                } else if (asort == DOUBLE) {
                    binarymd.constant = number0.doubleValue() - number1.doubleValue();
                }
            } else if (ctx.LSH() != null) {
                if (asort == INT) {
                    binarymd.constant = number0.intValue() << number1.intValue();
                } else if (asort == LONG) {
                    binarymd.constant = number0.longValue() << number1.longValue();
                }
            } else if (ctx.RSH() != null) {
                if (asort == INT) {
                    binarymd.constant = number0.intValue() >> number1.intValue();
                } else if (asort == LONG) {
                    binarymd.constant = number0.longValue() >> number1.longValue();
                }
            } else if (ctx.USH() != null) {
                if (asort == INT) {
                    binarymd.constant = number0.intValue() >>> number1.intValue();
                } else if (asort == LONG) {
                    binarymd.constant = number0.longValue() >>> number1.longValue();
                }
            } else if (ctx.BWAND() != null) {
                if (asort == INT) {
                    binarymd.constant = number0.intValue() & number1.intValue();
                } else if (asort == LONG) {
                    binarymd.constant = number0.longValue() & number1.longValue();
                }
            } else if (ctx.BWXOR() != null) {
                if (asort == INT) {
                    binarymd.constant = number0.intValue() ^ number1.intValue();
                } else if (asort == LONG) {
                    binarymd.constant = number0.longValue() ^ number1.longValue();
                }
            } else if (ctx.BWOR() != null) {
                if (asort == INT) {
                    binarymd.constant = number0.intValue() | number1.intValue();
                } else if (asort == LONG) {
                    binarymd.constant = number0.longValue() | number1.longValue();
                }
            } else {
                throw new IllegalStateException();
            }
        } else {
            markCast(expressionmd0, binarymd.castatypes[0], false);
            markCast(expressionmd1, binarymd.castatypes[0], false);
        }

        return null;
    }

    @Override
    public Void visitComp(final CompContext ctx) {
        final Metadata compmd = getMetadata(ctx);
        compmd.castnodes = new ParseTree[] {ctx};
        compmd.castatypes = new Type[] {Type.BOOLEAN_TYPE};

        final ExpressionContext ectx0 = ctx.expression(0);
        final Metadata expressionmd0 = createMetadata(ectx0);
        visit(ectx0);

        final ExpressionContext ectx1 = ctx.expression(1);
        final Metadata expressionmd1 = createMetadata(ectx1);
        visit(ectx1);

        if (isNumeric(expressionmd0.castatypes) && isNumeric(expressionmd1.castatypes)) {
            final Type apromote = getPromotion(new Metadata[]{expressionmd0, expressionmd1}, true);
            final int asort = apromote.getSort();
            Number number0 = null;
            Number number1 = null;

            if (expressionmd0.constant instanceof Number) {
                number0 = (Number) expressionmd0.constant;
            } else if (expressionmd0.constant instanceof Character) {
                number0 = getNFromC((char)expressionmd0.constant, apromote);
            }

            if (expressionmd1.constant instanceof Number) {
                number1 = (Number) expressionmd1.constant;
            } else if (expressionmd0.constant instanceof Character) {
                number1 = getNFromC((char)expressionmd1.constant, apromote);
            }

            if (number0 != null && number1 != null) {
                if (ctx.EQ() != null) {
                    if (asort == INT) {
                        compmd.constant = number0.intValue() == number1.intValue();
                    } else if (asort == LONG) {
                        compmd.constant = number0.longValue() == number1.longValue();
                    } else if (asort == FLOAT) {
                        compmd.constant = number0.floatValue() == number1.floatValue();
                    } else if (asort == DOUBLE) {
                        compmd.constant = number0.doubleValue() == number1.doubleValue();
                    }
                } else if (ctx.NE() != null) {
                    if (asort == INT) {
                        compmd.constant = number0.intValue() != number1.intValue();
                    } else if (asort == LONG) {
                        compmd.constant = number0.longValue() != number1.longValue();
                    } else if (asort == FLOAT) {
                        compmd.constant = number0.floatValue() != number1.floatValue();
                    } else if (asort == DOUBLE) {
                        compmd.constant = number0.doubleValue() != number1.doubleValue();
                    }
                } else if (ctx.GTE() != null) {
                    if (asort == INT) {
                        compmd.constant = number0.intValue() >= number1.intValue();
                    } else if (asort == LONG) {
                        compmd.constant = number0.longValue() >= number1.longValue();
                    } else if (asort == FLOAT) {
                        compmd.constant = number0.floatValue() >= number1.floatValue();
                    } else if (asort == DOUBLE) {
                        compmd.constant = number0.doubleValue() >= number1.doubleValue();
                    }
                } else if (ctx.GT() != null) {
                    if (asort == INT) {
                        compmd.constant = number0.intValue() > number1.intValue();
                    } else if (asort == LONG) {
                        compmd.constant = number0.longValue() > number1.longValue();
                    } else if (asort == FLOAT) {
                        compmd.constant = number0.floatValue() > number1.floatValue();
                    } else if (asort == DOUBLE) {
                        compmd.constant = number0.doubleValue() > number1.doubleValue();
                    }
                } else if (ctx.LTE() != null) {
                    if (asort == INT) {
                        compmd.constant = number0.intValue() <= number1.intValue();
                    } else if (asort == LONG) {
                        compmd.constant = number0.longValue() <= number1.longValue();
                    } else if (asort == FLOAT) {
                        compmd.constant = number0.floatValue() <= number1.floatValue();
                    } else if (asort == DOUBLE) {
                        compmd.constant = number0.doubleValue() <= number1.doubleValue();
                    }
                } else if (ctx.LT() != null) {
                    if (asort == INT) {
                        compmd.constant = number0.intValue() < number1.intValue();
                    } else if (asort == LONG) {
                        compmd.constant = number0.longValue() < number1.longValue();
                    } else if (asort == FLOAT) {
                        compmd.constant = number0.floatValue() < number1.floatValue();
                    } else if (asort == DOUBLE) {
                        compmd.constant = number0.doubleValue() < number1.doubleValue();
                    }
                } else {
                    throw new IllegalStateException();
                }
            } else {
                markCast(expressionmd0, Type.BOOLEAN_TYPE, false);
                markCast(expressionmd1, Type.BOOLEAN_TYPE, false);
            }

        } else if (isType(expressionmd0.castatypes, Type.BOOLEAN_TYPE, true) &&
                isType(expressionmd1.castatypes, Type.BOOLEAN_TYPE, true)) {
            if (expressionmd0.constant != null && expressionmd1.constant != null) {
                if (ctx.EQ() != null) {
                    compmd.constant = (boolean)expressionmd0.constant == (boolean)expressionmd1.constant;
                } else if (ctx.NE() != null) {
                    compmd.constant = (boolean)expressionmd0.constant != (boolean)expressionmd1.constant;
                } else {
                    throw new IllegalStateException();
                }
            } else {
                markCast(expressionmd0, Type.BOOLEAN_TYPE, false);
                markCast(expressionmd1, Type.BOOLEAN_TYPE, false);
            }
        } else {
            if (expressionmd0.constant != null && expressionmd1.constant != null) {
                if (ctx.EQ() != null) {
                    compmd.constant = expressionmd0.constant == expressionmd1.constant;
                } else if (ctx.NE() != null) {
                    compmd.constant = expressionmd0.constant != expressionmd1.constant;
                } else {
                    throw new IllegalStateException();
                }
            } else {
                markCast(expressionmd0, Type.BOOLEAN_TYPE, false);
                markCast(expressionmd1, Type.BOOLEAN_TYPE, false);
            }
        }

        return null;
    }

    @Override
    public Void visitBool(BoolContext ctx) {
        final Metadata boolmd = getMetadata(ctx);
        boolmd.castnodes = new ParseTree[] {ctx};
        boolmd.castatypes = new Type[] {Type.BOOLEAN_TYPE};

        final ExpressionContext ectx0 = ctx.expression(0);
        final Metadata expressionmd0 = createMetadata(ectx0);
        visit(ectx0);

        final ExpressionContext ectx1 = ctx.expression(1);
        final Metadata expressionmd1 = createMetadata(ectx1);
        visit(ectx1);

        if (isType(expressionmd0.castatypes, Type.BOOLEAN_TYPE, true) &&
                isType(expressionmd1.castatypes, Type.BOOLEAN_TYPE, true)) {
            if (expressionmd0.constant != null && expressionmd1.constant != null) {
                if (ctx.BOOLAND() != null) {
                    boolmd.constant = (boolean)expressionmd0.constant && (boolean)expressionmd1.constant;
                } else if (ctx.BOOLOR() != null) {
                    boolmd.constant = (boolean)expressionmd0.constant || (boolean)expressionmd1.constant;
                } else {
                    throw new IllegalStateException();
                }
            } else {
                markCast(expressionmd0, Type.BOOLEAN_TYPE, false);
                markCast(expressionmd1, Type.BOOLEAN_TYPE, false);
            }
        }

        return null;
    }

    @Override
    public Void visitConditional(PainlessParser.ConditionalContext ctx) {
        final Metadata conditionalmd = getMetadata(ctx);

        final ExpressionContext ectx0 = ctx.expression(0);
        final Metadata expressionmd0 = createMetadata(ectx0);
        visit(ectx0);

        if (expressionmd0.constant instanceof Boolean) {
            conditionalmd.constant = expressionmd0.constant;
        }

        final ExpressionContext ectx1 = ctx.expression(1);
        final Metadata expressionmd1 = createMetadata(ectx1);
        visit(ectx1);

        final ExpressionContext ectx2 = ctx.expression(2);
        final Metadata expressionmd2 = createMetadata(ectx2);
        visit(ectx2);

        boolean constant0 = false;
        boolean constant1 = false;

        if (conditionalmd.constant != null) {
            if ((boolean)conditionalmd.constant && expressionmd1.constant != null) {
                constant0 = true;
            } else if (expressionmd2.constant != null) {
                constant1 = true;
            }
        }

        if (constant0) {
            conditionalmd.constant = expressionmd1.constant;
            conditionalmd.castatypes[0] = expressionmd1.castatypes[0];
        } else if (constant1) {
            conditionalmd.constant = expressionmd2.constant;
            conditionalmd.castatypes[0] = expressionmd2.castatypes[0];
        } else {
            final int nodeslen1 = expressionmd1.castnodes.length;
            final int nodeslen2 = expressionmd2.castnodes.length;
            conditionalmd.castnodes = new ParseTree[nodeslen1 + nodeslen2];
            System.arraycopy(expressionmd1.castnodes, 0, conditionalmd.castnodes, 0, nodeslen1);
            System.arraycopy(expressionmd2.castnodes, 0, conditionalmd.castnodes, nodeslen1, nodeslen2);

            final int castslen1 = expressionmd1.castatypes.length;
            final int castslen2 = expressionmd2.castatypes.length;
            conditionalmd.castnodes = new ParseTree[castslen1 + castslen2];
            System.arraycopy(expressionmd1.castatypes, 0, conditionalmd.castatypes, 0, castslen1);
            System.arraycopy(expressionmd2.castatypes, 0, conditionalmd.castatypes, castslen1, castslen2);

            conditionalmd.conditional = true;
        }

        return null;
    }

    @Override
    public Void visitAssignment(final AssignmentContext ctx) {
        final Metadata assignmentmd = getMetadata(ctx);

        final ExtstartContext ectx0 = ctx.extstart();
        Metadata extstartmd = createMetadata(ectx0);
        visit(ectx0);

        if (extstartmd.pexternal == null) {
            throw new IllegalArgumentException();
        }

        if (extstartmd.pexternal.isReadOnly()) {
            throw new IllegalArgumentException();
        }

        if (assignmentmd.righthand) {
            assignmentmd.castnodes = new ParseTree[] {ctx};
            assignmentmd.castatypes = new Type[] {extstartmd.pexternal.getAType()};
        }

        final ExpressionContext ectx1 = ctx.expression();
        final Metadata expressionmd = createMetadata(ectx1);
        expressionmd.righthand = true;
        visit(ectx1);
        markCast(expressionmd, extstartmd.pexternal.getAType(), false);

        assignmentmd.statement = true;

        return null;
    }

    @Override
    public Void visitExtstart(final ExtstartContext ctx) {
        final Metadata extstartmd = getMetadata(ctx);
        extstartmd.pexternal = new PExternal();

        final ExtprecContext ectx0 = ctx.extprec();
        final ExtcastContext ectx1 = ctx.extcast();
        final ExttypeContext ectx2 = ctx.exttype();
        final ExtmemberContext ectx3 = ctx.extmember();

        if (ectx0 != null) {
            final Metadata extprecmd = createMetadata(ectx0);
            extprecmd.pexternal = extstartmd.pexternal;
            visit(ectx0);
        } else if (ectx1 != null) {
            final Metadata extcastmd = createMetadata(ectx1);
            extcastmd.pexternal = extstartmd.pexternal;
            visit(ectx1);
        } else if (ectx2 != null) {
            final Metadata exttypemd = createMetadata(ectx2);
            exttypemd.pexternal = extstartmd.pexternal;
            visit(ectx2);
        } else if (ectx3 != null) {
            final Metadata extmembermd = createMetadata(ectx3);
            extmembermd.pexternal = extstartmd.pexternal;
            visit(ectx3);
        } else {
            throw new IllegalStateException();
        }

        extstartmd.statement = extstartmd.pexternal.isCall();
        extstartmd.castnodes = new ParseTree[] {ctx};
        extstartmd.castatypes = new Type[] {extstartmd.pexternal.getAType()};

        return null;
    }

    @Override
    public Void visitExtprec(final ExtprecContext ctx) {
        final Metadata extprecmd0 = getMetadata(ctx);

        final ExtprecContext ectx0 = ctx.extprec();
        final ExtcastContext ectx1 = ctx.extcast();
        final ExttypeContext ectx2 = ctx.exttype();
        final ExtmemberContext ectx3 = ctx.extmember();

        if (ectx0 != null) {
            final Metadata extprecmd1 = createMetadata(ectx0);
            extprecmd1.pexternal = extprecmd0.pexternal;
            visit(ectx0);
        } else if (ectx1 != null) {
            final Metadata extcastmd = createMetadata(ectx1);
            extcastmd.pexternal = extprecmd0.pexternal;
            visit(ectx1);
        } else if (ectx2 != null) {
            final Metadata exttypemd = createMetadata(ectx2);
            exttypemd.pexternal = extprecmd0.pexternal;
            visit(ectx2);
        } else if (ectx3 != null) {
            final Metadata extmembermd = createMetadata(ectx3);
            extmembermd.pexternal = extprecmd0.pexternal;
            visit(ectx3);
        } else {
            throw new IllegalStateException();
        }

        final ExtdotContext ectx4 = ctx.extdot();
        final ExtarrayContext ectx5 = ctx.extarray();

        if (ectx4 != null) {
            final Metadata extdotmd = createMetadata(ectx4);
            extdotmd.pexternal = extprecmd0.pexternal;
            visit(ectx4);
        } else if (ectx5 != null) {
            final Metadata extarraymd = createMetadata(ectx5);
            extarraymd.pexternal = extprecmd0.pexternal;
            visit(ectx5);
        }

        return null;
    }

    @Override
    public Void visitExtcast(final ExtcastContext ctx) {
        final Metadata extcastmd0 = getMetadata(ctx);

        final ExtprecContext ectx0 = ctx.extprec();
        final ExtcastContext ectx1 = ctx.extcast();
        final ExttypeContext ectx2 = ctx.exttype();
        final ExtmemberContext ectx3 = ctx.extmember();

        if (ectx0 != null) {
            final Metadata extprecmd1 = createMetadata(ectx0);
            extprecmd1.pexternal = extcastmd0.pexternal;
            visit(ectx0);
        } else if (ectx1 != null) {
            final Metadata extcastmd1 = createMetadata(ectx1);
            extcastmd1.pexternal = extcastmd0.pexternal;
            visit(ectx1);
        } else if (ectx2 != null) {
            final Metadata exttypemd = createMetadata(ectx2);
            exttypemd.pexternal = extcastmd0.pexternal;
            visit(ectx2);
        } else if (ectx3 != null) {
            final Metadata extmembermd = createMetadata(ectx3);
            extmembermd.pexternal = extcastmd0.pexternal;
            visit(ectx3);
        } else {
            throw new IllegalStateException();
        }

        final DecltypeContext dctx = ctx.decltype();
        final Metadata decltypemd = createMetadata(dctx);
        visit(dctx);

        final Type afrom = extcastmd0.pexternal.getAType();
        final Type ato = decltypemd.adecltype;

        //TODO: check cast legality
        //extcastmd0.pexternal.addSegment(CAST, new PCast(afrom, ato));

        return null;
    }

    @Override
    public Void visitExtarray(final ExtarrayContext ctx) {
        final Metadata extarraymd0 = getMetadata(ctx);

        final ExpressionContext ectx0 = ctx.expression();
        final Metadata expressionmd = createMetadata(ectx0);
        visit(ectx0);
        markCast(expressionmd, INT_TYPE, false);

        extarraymd0.pexternal.addSegment(PainlessExternal.ARRAY, ectx0);

        final ExtdotContext ectx1 = ctx.extdot();
        final ExtarrayContext ectx2 = ctx.extarray();

        if (ectx1 != null) {
            final Metadata extdotmd = createMetadata(ectx1);
            extdotmd.pexternal = extarraymd0.pexternal;
            visit(ectx1);
        } else if (ectx2 != null) {
            final Metadata extarraymd1 = createMetadata(ectx2);
            extarraymd1.pexternal = extarraymd0.pexternal;
            visit(ectx2);
        }

        return null;
    }

    @Override
    public Void visitExtdot(final ExtdotContext ctx) {
        final Metadata extdotmd = getMetadata(ctx);

        final ExtcallContext ectx0 = ctx.extcall();
        final ExtmemberContext ectx1 = ctx.extmember();

        if (ectx0 != null) {
            final Metadata extcallmd = createMetadata(ectx0);
            extcallmd.pexternal = extdotmd.pexternal;
            visit(ectx0);
        } else if (ectx1 != null) {
            final Metadata extmembermd = createMetadata(ectx1);
            extmembermd.pexternal = extdotmd.pexternal;
            visit(ectx1);
        }

        return null;
    }

    @Override
    public Void visitExttype(final ExttypeContext ctx) {
        final Metadata exttypemd = getMetadata(ctx);
        final String ptype = ctx.TYPE().getText();
        final Type atype = ptypes.getATypeFromPType(ptype);

        if (atype == null) {
            throw new IllegalArgumentException();
        }

        exttypemd.pexternal.addSegment(PainlessExternal.TYPE, atype);

        final ExtdotContext ectx = ctx.extdot();
        final Metadata extdotmd = createMetadata(ectx);
        extdotmd.pexternal = exttypemd.pexternal;
        visit(ectx);

        return null;
    }

    @Override
    public Void visitExtcall(final ExtcallContext ctx) {
        final Metadata extcallmd = getMetadata(ctx);

        final Type adecltype = extcallmd.pexternal.getAType();
        final PClass pclass = ptypes.getPClassFromAType(adecltype);

        if (pclass == null) {
            throw new IllegalArgumentException();
        }

        final String pname = ctx.ID().getText();
        final boolean statik = extcallmd.pexternal.isStatic();

        final ArgumentsContext actx = ctx.arguments();
        final Metadata argumentsmd = createMetadata(actx);
        final List<ExpressionContext> arguments = ctx.arguments().expression();
        argumentsmd.pexternal = extcallmd.pexternal;
        argumentsmd.castnodes = new ParseTree[arguments.size()];
        arguments.toArray(argumentsmd.castnodes);

        if (statik && "makearray".equals(pname)) {
            extcallmd.pexternal.addSegment(AMAKE, arguments.size());

            Type[] atypes = new Type[arguments.size()];
            Arrays.fill(atypes, Type.INT_TYPE);
            argumentsmd.castatypes = atypes;
        } else {
            final PConstructor pconstructor = statik ? pclass.getPConstructor(pname) : null;
            final PMethod pmethod = statik ? pclass.getPFunction(pname) : pclass.getPMethod(pname);

            if (pconstructor != null) {
                extcallmd.pexternal.addSegment(CONSTRUCTOR, pconstructor);
                argumentsmd.castatypes = pconstructor.amethod.getArgumentTypes();
            } else if (pmethod != null) {
                extcallmd.pexternal.addSegment(METHOD, pmethod);
                argumentsmd.castatypes = pmethod.amethod.getArgumentTypes();
            }
            else {
                throw new IllegalArgumentException();
            }

            visit(actx);
        }

        visit(actx);

        final ExtdotContext ectx0 = ctx.extdot();
        final ExtarrayContext ectx1 = ctx.extarray();

        if (ectx0 != null) {
            final Metadata extdotmd = createMetadata(ectx0);
            extdotmd.pexternal = extcallmd.pexternal;
            visit(ectx0);
        } else if (ectx1 != null) {
            final Metadata extdotmd = createMetadata(ectx1);
            extdotmd.pexternal = extcallmd.pexternal;
            visit(ectx1);
        }

        return null;
    }

    @Override
    public Void visitExtmember(final ExtmemberContext ctx) {
        final Metadata extmembermd = getMetadata(ctx);
        final Type atype = extmembermd.pexternal.getAType();
        final String pname = ctx.ID().getText();

        if (atype == null) {
            final Variable variable = getVariable(pname);

            if (variable == null) {
                throw new IllegalArgumentException();
            }

            extmembermd.pexternal.addSegment(VARIABLE, variable);
        } else {
            if (atype.getSort() == ARRAY) {
                if ("length".equals(pname)) {
                    extmembermd.pexternal.addSegment(ALENGTH, Type.INT_TYPE);
                } else {
                    throw new IllegalArgumentException();
                }
            } else {
                final PClass pclass = ptypes.getPClassFromAType(atype);

                if (pclass == null) {
                    throw new IllegalArgumentException();
                }

                final boolean statik = extmembermd.pexternal.isStatic();
                final PField pmember = statik ? pclass.getPStatic(pname) : pclass.getPMember(pname);

                if (pmember == null) {
                    throw new IllegalArgumentException();
                }

                extmembermd.pexternal.addSegment(FIELD, pmember);
            }
        }

        final ExtdotContext ectx0 = ctx.extdot();
        final ExtarrayContext ectx1 = ctx.extarray();

        if (ectx0 != null) {
            final Metadata extdotmd = createMetadata(ectx0);
            extdotmd.pexternal = extmembermd.pexternal;
            visit(ectx0);
        } else if (ectx1 != null) {
            final Metadata extdotmd = createMetadata(ectx1);
            extdotmd.pexternal = extmembermd.pexternal;
            visit(ectx1);
        }

        return null;
    }

    @Override
    public Void visitArguments(final ArgumentsContext ctx) {
        final Metadata argumentsmd = getMetadata(ctx);
        final ParseTree[] nodes = argumentsmd.castnodes;
        final Type[] atypes = argumentsmd.castatypes;

        if (nodes == null || atypes == null) {
            throw new IllegalArgumentException();
        }

        if (nodes.length != atypes.length) {
            throw new IllegalArgumentException();
        }

        final int arguments = nodes.length;

        for (int argument = 0; argument < arguments; ++argument) {
            final ParseTree ectx = nodes[argument];
            final Metadata nodemd = createMetadata(ectx);
            visit(ectx);
            markCast(nodemd, atypes[argument], false);

            argumentsmd.pexternal.addSegment(ARGUMENT, ectx);
        }

        return null;
    }*/
}
