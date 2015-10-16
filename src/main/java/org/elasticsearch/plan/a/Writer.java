package org.elasticsearch.plan.a;

/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import org.antlr.v4.runtime.tree.ParseTree;
import org.objectweb.asm.*;

import static org.elasticsearch.plan.a.Adapter.*;
import static org.elasticsearch.plan.a.Definition.*;
import static org.elasticsearch.plan.a.PlanAParser.*;

class Writer extends PlanABaseVisitor<Void>{
    final static String BASE_CLASS_NAME = Executable.class.getName();
    final static String CLASS_NAME = BASE_CLASS_NAME + "$CompiledPlanAExecutable";
    final static String BASE_CLASS_INTERNAL = Executable.class.getName().replace('.', '/');
    final static String CLASS_INTERNAL = BASE_CLASS_INTERNAL + "$CompiledPlanAExecutable";

    static byte[] write(Adapter adapter) {
        Writer writer = new Writer(adapter);

        return writer.getBytes();
    }

    private final Adapter adapter;
    private final Caster caster;
    private final ParseTree root;
    private final String source;

    private ClassWriter writer;
    private MethodVisitor execute;

    private Writer(final Adapter adapter) {
        this.adapter = adapter;
        caster = adapter.caster;
        root = adapter.root;
        source = adapter.source;

        writeBegin();
        writeConstructor();
        writeExecute();
        writeEnd();
    }

    private void writeBegin() {
        final int compute = ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS;
        final int version = Opcodes.V1_7;
        final int access = Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER | Opcodes.ACC_FINAL | Opcodes.ACC_SYNTHETIC;
        final String base = BASE_CLASS_INTERNAL;
        final String name = CLASS_INTERNAL;

        writer = new ClassWriter(compute);
        writer.visit(version, access, name, null, base, null);
        writer.visitSource(source, null);
    }

    private void writeConstructor() {
        final int access = Opcodes.ACC_PUBLIC | Opcodes.ACC_SYNTHETIC;
        final String aname = "<init>";
        final String adescriptor = "(Ljava/lang/String;Ljava/lang/String;)V";

        final MethodVisitor constructor = writer.visitMethod(access, aname, adescriptor, null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitVarInsn(Opcodes.ALOAD, 1);
        constructor.visitVarInsn(Opcodes.ALOAD, 2);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, BASE_CLASS_INTERNAL, aname, adescriptor, false);
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(0, 0);
        constructor.visitEnd();
    }

    private void writeExecute() {
        final int access = Opcodes.ACC_PUBLIC | Opcodes.ACC_SYNTHETIC;
        final String aname = "execute";
        final String adescriptor = "(Ljava/util/Map;)Ljava/lang/Object;";
        final String signature = "(Ljava/util/Map<Ljava/lang/String;Ljava/lang/Object;>;)Ljava/lang/Object;";

        execute = writer.visitMethod(access, aname, adescriptor, signature, null);
        execute.visitCode();
        visit(root);
        execute.visitMaxs(0, 0);
        execute.visitEnd();
    }

    @Override
    public Void visitSource(final SourceContext ctx) {
        final StatementMetadata sourcesmd = adapter.getStatementMetadata(ctx);

        for (final StatementContext sctx : ctx.statement()) {
            visit(sctx);
        }

        if (!sourcesmd.allReturn) {
            execute.visitInsn(Opcodes.ACONST_NULL);
            execute.visitInsn(Opcodes.ARETURN);
        }

        return null;
    }

    @Override
    public Void visitIf(final IfContext ctx) {
        final ExpressionContext exprctx = ctx.expression();
        final boolean els = ctx.ELSE() != null;
        final Branch branch = adapter.markBranch(ctx, exprctx);
        branch.end = new Label();
        branch.fals = els ? new Label() : branch.end;

        visit(exprctx);

        final BlockContext blockctx0 = ctx.block(0);
        final StatementMetadata blockmd0 = adapter.getStatementMetadata(blockctx0);
        visit(blockctx0);

        if (els) {
            if (!blockmd0.allExit) { // TODO: this needs downcast check fo all paths exit
                execute.visitJumpInsn(Opcodes.GOTO, branch.end);
            }

            execute.visitLabel(branch.fals);
            visit(ctx.block(1));
        }

        execute.visitLabel(branch.end);

        return null;
    }

    @Override
    public Void visitWhile(final WhileContext ctx) {
        final ExpressionContext exprctx = ctx.expression();
        final Branch branch = adapter.markBranch(ctx, exprctx);
        branch.begin = new Label();
        branch.end = new Label();
        branch.fals = branch.end;

        adapter.pushJump(branch);
        execute.visitLabel(branch.begin);
        visit(exprctx);

        final BlockContext blockctx = ctx.block();
        boolean allexit = false;

        if (blockctx != null) {
            StatementMetadata blocksmd = adapter.getStatementMetadata(blockctx);
            allexit = blocksmd.allExit;
            visit(blockctx);
        }

        if (!allexit) {
            execute.visitJumpInsn(Opcodes.GOTO, branch.begin);
        }

        execute.visitLabel(branch.end);
        adapter.popJump();

        return null;
    }

    @Override
    public Void visitDo(final DoContext ctx) {
        final ExpressionContext exprctx = ctx.expression();
        final Branch branch = adapter.markBranch(ctx, exprctx);
        branch.begin = new Label();
        branch.end = new Label();
        branch.fals = branch.end;

        adapter.pushJump(branch);
        execute.visitLabel(branch.begin);

        final BlockContext bctx = ctx.block();
        final StatementMetadata blocksmd = adapter.getStatementMetadata(bctx);
        visit(bctx);

        visit(exprctx);

        if (!blocksmd.allExit) {
            execute.visitJumpInsn(Opcodes.GOTO, branch.begin);
        }

        execute.visitLabel(branch.end);
        adapter.popJump();

        return null;
    }

    @Override
    public Void visitFor(final ForContext ctx) {
        final ExpressionContext exprctx0 = ctx.expression(0);
        final ExpressionContext exprctx1 = ctx.expression(1);
        final Branch branch = adapter.markBranch(ctx, exprctx0);
        final Label start = new Label();
        branch.begin = exprctx1 == null ? start : new Label();
        branch.end = new Label();
        branch.fals = branch.end;

        adapter.pushJump(branch);

        if (ctx.declaration() != null) {
            visit(ctx.declaration());
        }

        execute.visitLabel(start);

        if (exprctx0 != null) {
            visit(exprctx0);
        }

        final BlockContext blockctx = ctx.block();
        boolean allexit = false;

        if (blockctx != null) {
            StatementMetadata blocksmd = adapter.getStatementMetadata(blockctx);
            allexit = blocksmd.allExit;
            visit(blockctx);
        }

        if (exprctx1 != null) {
            execute.visitLabel(branch.begin);
            visit(exprctx1);
        }

        if (exprctx1 != null || !allexit) {
            execute.visitJumpInsn(Opcodes.GOTO, start);
        }

        execute.visitLabel(branch.end);
        adapter.popJump();

        return null;
    }

    @Override
    public Void visitDecl(final DeclContext ctx) {
        visit(ctx.declaration());

        return null;
    }

    @Override
    public Void visitContinue(final ContinueContext ctx) {
        final Branch jump = adapter.peekJump();
        execute.visitJumpInsn(Opcodes.GOTO, jump.begin);

        return null;
    }

    @Override
    public Void visitBreak(final BreakContext ctx) {
        final Branch jump = adapter.peekJump();
        execute.visitJumpInsn(Opcodes.GOTO, jump.end);

        return null;
    }

    @Override
    public Void visitReturn(final ReturnContext ctx) {
        visit(ctx.expression());
        execute.visitInsn(Opcodes.ARETURN);

        return null;
    }

    @Override
    public Void visitExpr(final ExprContext ctx) {
        visit(ctx.expression());

        return null;
    }

    @Override
    public Void visitMultiple(final MultipleContext ctx) {
        for (final StatementContext sctx : ctx.statement()) {
            visit(sctx);
        }

        return null;
    }

    @Override
    public Void visitSingle(final SingleContext ctx) {
        visit(ctx.statement());

        return null;
    }

    @Override
    public Void visitEmpty(final EmptyContext ctx) {
        throw new UnsupportedOperationException(); // TODO: message
    }

    @Override
    public Void visitDeclaration(DeclarationContext ctx) {
        for (final DeclvarContext declctx : ctx.declvar()) {
            visit(declctx);
        }

        return null;
    }

    @Override
    public Void visitDecltype(final DecltypeContext ctx) {
        throw new UnsupportedOperationException(); //TODO: message
    }

    @Override
    public Void visitDeclvar(final DeclvarContext ctx) {
        final ExpressionMetadata declemd = adapter.getExpressionMetadata(ctx);
        final Variable variable = (Variable)declemd.postConst;

        final ExpressionContext exprctx = ctx.expression();
        final boolean initialize = exprctx == null;

        if (!initialize) {
            visit(exprctx);
        }

        switch (variable.type.metadata) {
            case VOID:
                throw new IllegalStateException(); // TODO: message
            case BOOL:
            case BYTE:
            case SHORT:
            case CHAR:
            case INT:    if (initialize) writeNumeric(0);    execute.visitVarInsn(Opcodes.ISTORE, variable.slot); break;
            case LONG:   if (initialize) writeNumeric(0L);   execute.visitVarInsn(Opcodes.LSTORE, variable.slot); break;
            case FLOAT:  if (initialize) writeNumeric(0.0F); execute.visitVarInsn(Opcodes.FSTORE, variable.slot); break;
            case DOUBLE: if (initialize) writeNumeric(0.0);  execute.visitVarInsn(Opcodes.DSTORE, variable.slot); break;
            default:
                if (initialize) {
                    execute.visitInsn(Opcodes.ACONST_NULL);
                }

                execute.visitVarInsn(Opcodes.ASTORE, variable.slot);
        }

        return null;
    }

    @Override
    public Void visitPrecedence(final PrecedenceContext ctx) {
        throw new UnsupportedOperationException(); // TODO: message
    }

    @Override
    public Void visitNumeric(final NumericContext ctx) {
        final ExpressionMetadata numericemd = adapter.getExpressionMetadata(ctx);
        final Object postConst = numericemd.postConst;

        if (postConst == null) {
            writeNumeric(numericemd.preConst);
            caster.checkWriteCast(execute, numericemd);
        } else {
            writeConstant(postConst);
        }

        adapter.checkWriteBranch(execute, ctx);

        return null;
    }

    @Override
    public Void visitString(final StringContext ctx) {
        final ExpressionMetadata stringemd = adapter.getExpressionMetadata(ctx);
        final Object postConst = stringemd.postConst;

        if (postConst == null) {
            writeString(stringemd.preConst);
            caster.checkWriteCast(execute, stringemd);
        } else {
            writeConstant(postConst);
        }

        adapter.checkWriteBranch(execute, ctx);

        return null;
    }

    @Override
    public Void visitChar(final CharContext ctx) {
        final ExpressionMetadata charemd = adapter.getExpressionMetadata(ctx);
        final Object postConst = charemd.postConst;

        if (postConst == null) {
            writeString(charemd.preConst);
            caster.checkWriteCast(execute, charemd);
        } else {
            writeConstant(postConst);
        }

        adapter.checkWriteBranch(execute, ctx);

        return null;
    }

    @Override
    public Void visitTrue(final TrueContext ctx) {
        final ExpressionMetadata trueemd = adapter.getExpressionMetadata(ctx);
        final Object postConst = trueemd.postConst;
        final Branch branch = adapter.getBranch(ctx);

        if (branch == null) {
            if (postConst == null) {
                writeBoolean(true);
                caster.checkWriteCast(execute, trueemd);
            } else {
                writeConstant(postConst);
            }
        } else if (branch.tru != null) {
            execute.visitJumpInsn(Opcodes.GOTO, branch.tru);
        }

        return null;
    }

    @Override
    public Void visitFalse(final FalseContext ctx) {
        final ExpressionMetadata falseemd = adapter.getExpressionMetadata(ctx);
        final Object postConst = falseemd.postConst;
        final Branch branch = adapter.getBranch(ctx);

        if (branch == null) {
            if (postConst == null) {
                writeBoolean(false);
                caster.checkWriteCast(execute, falseemd);
            } else {
                writeConstant(postConst);
            }
        } else if (branch.fals != null) {
            execute.visitJumpInsn(Opcodes.GOTO, branch.fals);
        }

        return null;
    }

    @Override
    public Void visitNull(final NullContext ctx) {
        final ExpressionMetadata nullemd = adapter.getExpressionMetadata(ctx);

        execute.visitInsn(Opcodes.ACONST_NULL);
        caster.checkWriteCast(execute, nullemd);
        adapter.checkWriteBranch(execute, ctx);

        return null;
    }

    @Override
    public Void visitCat(CatContext ctx) {
        final ExpressionMetadata catemd = adapter.getExpressionMetadata(ctx);
        final boolean strings = adapter.getStrings(ctx);

        if (catemd.postConst != null) {
            writeConstant(catemd.postConst);
        } else {
            if (!strings) {
                writeNewStrings();
            }

            final ExpressionContext exprctx0 = ctx.expression(0);
            final ExpressionMetadata expremd0 = adapter.getExpressionMetadata(exprctx0);
            adapter.markStrings(exprctx0);
            visit(exprctx0);

            if (adapter.getStrings(exprctx0)) {
                writeAppendStrings(expremd0.to.metadata);
                adapter.unmarkStrings(exprctx0);
            }

            final ExpressionContext exprctx1 = ctx.expression(1);
            final ExpressionMetadata expremd1 = adapter.getExpressionMetadata(exprctx1);
            adapter.markStrings(exprctx1);
            visit(exprctx1);

            if (adapter.getStrings(exprctx1)) {
                writeAppendStrings(expremd1.to.metadata);
                adapter.unmarkStrings(exprctx1);
            }

            if (strings) {
                adapter.unmarkStrings(ctx);
            } else {
                writeToStrings();
            }

            caster.checkWriteCast(execute, catemd);
        }

        adapter.checkWriteBranch(execute, ctx);

        return null;
    }

    @Override
    public Void visitExt(final ExtContext ctx) {
        final External external = adapter.getExternal(ctx);
        external.setWriter(this, execute);
        external.write(ctx);

        return null;
    }


    @Override
    public Void visitPostinc(final PostincContext ctx) {
        final External external = adapter.getExternal(ctx);
        external.setWriter(this, execute);
        external.write(ctx);

        return null;
    }

    @Override
    public Void visitPreinc(final PreincContext ctx) {
        final External external = adapter.getExternal(ctx);
        external.setWriter(this, execute);
        external.write(ctx);

        return null;
    }

    @Override
    public Void visitUnary(final UnaryContext ctx) {
        final ExpressionMetadata unaryemd = adapter.getExpressionMetadata(ctx);
        final Object postConst = unaryemd.postConst;
        final Branch branch = adapter.getBranch(ctx);

        if (postConst == null) {
            final ExpressionContext exprctx = ctx.expression();

            if (ctx.BOOLNOT() != null) {
                final Branch local = adapter.markBranch(ctx, exprctx);

                if (branch == null) {
                    local.fals = new Label();
                    final Label aend = new Label();

                    visit(exprctx);

                    execute.visitInsn(Opcodes.ICONST_0);
                    execute.visitJumpInsn(Opcodes.GOTO, aend);
                    execute.visitLabel(local.fals);
                    execute.visitInsn(Opcodes.ICONST_1);
                    execute.visitLabel(aend);

                    caster.checkWriteCast(execute, unaryemd);
                } else {
                    local.tru = branch.fals;
                    local.fals = branch.tru;

                    visit(exprctx);
                }
            } else {
                final TypeMetadata metadata = unaryemd.from.metadata;

                visit(exprctx);

                if (ctx.BWNOT() != null) {
                    if      (metadata == TypeMetadata.INT)  { writeConstant(-1);  execute.visitInsn(Opcodes.IXOR); }
                    else if (metadata == TypeMetadata.LONG) { writeConstant(-1L); execute.visitInsn(Opcodes.LXOR); }
                    else {
                        throw new IllegalStateException(); // TODO: message
                    }
                } else if (ctx.SUB() != null) {
                    if      (metadata == TypeMetadata.INT)    execute.visitInsn(Opcodes.INEG);
                    else if (metadata == TypeMetadata.LONG)   execute.visitInsn(Opcodes.LNEG);
                    else if (metadata == TypeMetadata.FLOAT)  execute.visitInsn(Opcodes.FNEG);
                    else if (metadata == TypeMetadata.DOUBLE) execute.visitInsn(Opcodes.DNEG);
                    else {
                        throw new IllegalStateException(); // TODO: message
                    }
                }

                caster.checkWriteCast(execute, unaryemd);
                adapter.checkWriteBranch(execute, ctx);
            }
        } else {
            if (ctx.BOOLNOT() != null) {
                if (branch == null) {
                    writeConstant(postConst);
                } else {
                    if ((boolean)postConst && branch.tru != null) {
                        execute.visitJumpInsn(Opcodes.GOTO, branch.tru);
                    } else if (!(boolean)postConst && branch.fals != null) {
                        execute.visitJumpInsn(Opcodes.GOTO, branch.fals);
                    }
                }
            } else {
                writeConstant(postConst);
                adapter.checkWriteBranch(execute, ctx);
            }
        }

        return null;
    }

    @Override
    public Void visitCast(final CastContext ctx) {
        final ExpressionMetadata castemd = adapter.getExpressionMetadata(ctx);
        final Object postConst = castemd.postConst;

        if (postConst == null) {
            visit(ctx.expression());
            caster.checkWriteCast(execute, castemd);
        } else {
            writeConstant(postConst);
        }

        adapter.checkWriteBranch(execute, ctx);

        return null;
    }

    @Override
    public Void visitBinary(final BinaryContext ctx) {
        final ExpressionMetadata binaryemd = adapter.getExpressionMetadata(ctx);
        final Object postConst = binaryemd.postConst;

        if (postConst == null) {
            final ExpressionContext expr0 = ctx.expression(0);
            final ExpressionContext expr1 = ctx.expression(1);

            visit(expr0);
            visit(expr1);

            final TypeMetadata metadata = binaryemd.from.metadata;

            if      (ctx.MUL()   != null) writeBinaryInstruction(metadata, MUL);
            else if (ctx.DIV()   != null) writeBinaryInstruction(metadata, DIV);
            else if (ctx.REM()   != null) writeBinaryInstruction(metadata, REM);
            else if (ctx.SUB()   != null) writeBinaryInstruction(metadata, SUB);
            else if (ctx.LSH()   != null) writeBinaryInstruction(metadata, LSH);
            else if (ctx.USH()   != null) writeBinaryInstruction(metadata, USH);
            else if (ctx.RSH()   != null) writeBinaryInstruction(metadata, RSH);
            else if (ctx.BWAND() != null) writeBinaryInstruction(metadata, BWAND);
            else if (ctx.BWXOR() != null) writeBinaryInstruction(metadata, BWXOR);
            else if (ctx.BWOR()  != null) writeBinaryInstruction(metadata, BWOR);
            else if (ctx.ADD()   != null) writeBinaryInstruction(metadata, ADD);
            else {
                throw new IllegalStateException(); // TODO: message
            }

            caster.checkWriteCast(execute, binaryemd);
        } else {
            writeConstant(postConst);
        }

        adapter.checkWriteBranch(execute, ctx);

        return null;
    }

    @Override
    public Void visitComp(final CompContext ctx) {
        final ExpressionMetadata compemd = adapter.getExpressionMetadata(ctx);
        final Object postConst = compemd.postConst;
        final Branch branch = adapter.getBranch(ctx);

        if (postConst == null) {
            final ExpressionContext exprctx0 = ctx.expression(0);
            final ExpressionContext exprctx1 = ctx.expression(1);
            final ExpressionMetadata expremd1 = adapter.getExpressionMetadata(exprctx1);
            final TypeMetadata metadata = expremd1.to.metadata;

            visit(exprctx0);

            if (!expremd1.isNull) {
                visit(exprctx1);
            }

            final boolean tru = branch != null && branch.tru != null;
            final boolean fals = branch != null && branch.fals != null;
            final Label jump = tru ? branch.tru : fals ? branch.fals : new Label();
            final Label end = new Label();

            final boolean eq  = ctx.EQ()  != null && (tru || !fals) || ctx.NE()  != null && fals;
            final boolean ne  = ctx.NE()  != null && (tru || !fals) || ctx.EQ()  != null && fals;
            final boolean lt  = ctx.LT()  != null && (tru || !fals) || ctx.GTE() != null && fals;
            final boolean lte = ctx.LTE() != null && (tru || !fals) || ctx.GT()  != null && fals;
            final boolean gt  = ctx.GT()  != null && (tru || !fals) || ctx.LTE() != null && fals;
            final boolean gte = ctx.GTE() != null && (tru || !fals) || ctx.LT()  != null && fals;

            switch (metadata) {
                case VOID:
                    throw new IllegalStateException(); // TODO: message
                case BOOL:
                    if      (eq) execute.visitJumpInsn(Opcodes.IF_ICMPEQ, jump);
                    else if (ne) execute.visitJumpInsn(Opcodes.IF_ICMPNE, jump);
                    else {
                        throw new IllegalStateException(); // TODO: message
                    }

                    break;
                case BYTE:
                case SHORT:
                case CHAR:
                    throw new IllegalStateException(); // TODO: message
                case INT:
                    if      (eq)  execute.visitJumpInsn(Opcodes.IF_ICMPEQ, jump);
                    else if (ne)  execute.visitJumpInsn(Opcodes.IF_ICMPNE, jump);
                    else if (lt)  execute.visitJumpInsn(Opcodes.IF_ICMPLT, jump);
                    else if (lte) execute.visitJumpInsn(Opcodes.IF_ICMPLE, jump);
                    else if (gt)  execute.visitJumpInsn(Opcodes.IF_ICMPGT, jump);
                    else if (gte) execute.visitJumpInsn(Opcodes.IF_ICMPGE, jump);
                    else {
                        throw new IllegalStateException(); // TODO: message
                    }

                    break;
                case LONG:
                    execute.visitInsn(Opcodes.LCMP);

                    if      (eq)  execute.visitJumpInsn(Opcodes.IFEQ, jump);
                    else if (ne)  execute.visitJumpInsn(Opcodes.IFNE, jump);
                    else if (lt)  execute.visitJumpInsn(Opcodes.IFLT, jump);
                    else if (lte) execute.visitJumpInsn(Opcodes.IFLE, jump);
                    else if (gt)  execute.visitJumpInsn(Opcodes.IFGT, jump);
                    else if (gte) execute.visitJumpInsn(Opcodes.IFGE, jump);
                    else {
                        throw new IllegalStateException(); // TODO: message
                    }

                    break;
                case FLOAT:
                    if      (eq)  { execute.visitInsn(Opcodes.FCMPL); execute.visitJumpInsn(Opcodes.IFEQ, jump); }
                    else if (ne)  { execute.visitInsn(Opcodes.FCMPL); execute.visitJumpInsn(Opcodes.IFNE, jump); }
                    else if (lt)  { execute.visitInsn(Opcodes.FCMPG); execute.visitJumpInsn(Opcodes.IFLT, jump); }
                    else if (lte) { execute.visitInsn(Opcodes.FCMPG); execute.visitJumpInsn(Opcodes.IFLE, jump); }
                    else if (gt)  { execute.visitInsn(Opcodes.FCMPL); execute.visitJumpInsn(Opcodes.IFGT, jump); }
                    else if (gte) { execute.visitInsn(Opcodes.FCMPL); execute.visitJumpInsn(Opcodes.IFGE, jump); }
                    else {
                        throw new IllegalStateException(); // TODO: message
                    }

                    break;
                case DOUBLE:
                    if      (eq)  { execute.visitInsn(Opcodes.DCMPL); execute.visitJumpInsn(Opcodes.IFEQ, jump); }
                    else if (ne)  { execute.visitInsn(Opcodes.DCMPL); execute.visitJumpInsn(Opcodes.IFNE, jump); }
                    else if (lt)  { execute.visitInsn(Opcodes.DCMPG); execute.visitJumpInsn(Opcodes.IFLT, jump); }
                    else if (lte) { execute.visitInsn(Opcodes.DCMPG); execute.visitJumpInsn(Opcodes.IFLE, jump); }
                    else if (gt)  { execute.visitInsn(Opcodes.DCMPL); execute.visitJumpInsn(Opcodes.IFGT, jump); }
                    else if (gte) { execute.visitInsn(Opcodes.DCMPL); execute.visitJumpInsn(Opcodes.IFGE, jump); }
                    else {
                        throw new IllegalStateException(); // TODO: message
                    }

                    break;
                default:
                    if (eq) {
                        if (expremd1.isNull) {
                            execute.visitJumpInsn(Opcodes.IFNULL, jump);
                        } else {
                            execute.visitJumpInsn(Opcodes.IF_ACMPEQ, jump);
                        }
                    } else if (ne) {
                        if (expremd1.isNull) {
                            execute.visitJumpInsn(Opcodes.IFNONNULL, jump);
                        } else {
                            execute.visitJumpInsn(Opcodes.IF_ACMPNE, jump);
                        }
                    } else {
                        throw new IllegalStateException(); // TODO: message
                    }
            }

            if (branch == null) {
                execute.visitInsn(Opcodes.ICONST_0);
                execute.visitJumpInsn(Opcodes.GOTO, end);
                execute.visitLabel(jump);
                execute.visitInsn(Opcodes.ICONST_1);
                execute.visitLabel(end);

                caster.checkWriteCast(execute, compemd);
            }
        } else {
            if (branch == null) {
                writeConstant(postConst);
            } else {
                if ((boolean)postConst && branch.tru != null) {
                    execute.visitLabel(branch.tru);
                } else if (!(boolean)postConst && branch.fals != null) {
                    execute.visitLabel(branch.fals);
                }
            }
        }

        return null;
    }

    @Override
    public Void visitBool(final BoolContext ctx) {
        final ExpressionMetadata boolemd = adapter.getExpressionMetadata(ctx);
        final Object postConst = boolemd.postConst;
        final Branch branch = adapter.getBranch(ctx);

        if (postConst == null) {
            final ExpressionContext exprctx0 = ctx.expression(0);
            final ExpressionContext exprctx1 = ctx.expression(1);

            if (branch == null) {
                if (ctx.BOOLAND() != null) {
                    final Branch local = adapter.markBranch(ctx, exprctx0);
                    adapter.markBranch(exprctx0, exprctx1);
                    local.fals = new Label();
                    final Label end = new Label();

                    visit(exprctx0);
                    visit(exprctx1);

                    execute.visitInsn(Opcodes.ICONST_1);
                    execute.visitJumpInsn(Opcodes.GOTO, end);
                    execute.visitLabel(local.fals);
                    execute.visitInsn(Opcodes.ICONST_0);
                    execute.visitLabel(end);
                } else if (ctx.BOOLOR() != null) {
                    final Branch branch0 = adapter.markBranch(ctx, exprctx0);
                    branch0.tru = new Label();
                    final Branch branch1 = adapter.markBranch(ctx, exprctx1);
                    branch1.fals = new Label();
                    final Label aend = new Label();

                    visit(exprctx0);
                    visit(exprctx1);

                    execute.visitLabel(branch0.tru);
                    execute.visitInsn(Opcodes.ICONST_1);
                    execute.visitJumpInsn(Opcodes.GOTO, aend);
                    execute.visitLabel(branch1.fals);
                    execute.visitInsn(Opcodes.ICONST_0);
                    execute.visitLabel(aend);
                } else {
                    throw new IllegalStateException(); // TODO: message
                }

                caster.checkWriteCast(execute, boolemd);
            } else {
                if (ctx.BOOLAND() != null) {
                    final Branch branch0 = adapter.markBranch(ctx, exprctx0);
                    branch0.fals = branch.fals == null ? new Label() : branch.fals;
                    final Branch branch1 = adapter.markBranch(ctx, exprctx1);
                    branch1.tru = branch.tru;
                    branch1.fals = branch.fals;

                    visit(exprctx0);
                    visit(exprctx1);

                    if (branch.fals == null) {
                        execute.visitLabel(branch0.fals);
                    }
                } else if (ctx.BOOLOR() != null) {
                    final Branch branch0 = adapter.markBranch(ctx, exprctx0);
                    branch0.tru = branch.tru == null ? new Label() : branch.tru;
                    final Branch branch1 = adapter.markBranch(ctx, exprctx1);
                    branch1.tru = branch.tru;
                    branch1.fals = branch.fals;

                    visit(exprctx0);
                    visit(exprctx1);

                    if (branch.tru == null) {
                        execute.visitLabel(branch0.tru);
                    }
                } else {
                    throw new IllegalStateException(); // TODO: message
                }
            }
        } else {
            if (branch == null) {
                writeConstant(postConst);
            } else {
                if ((boolean)postConst && branch.tru != null) {
                    execute.visitLabel(branch.tru);
                } else if (!(boolean)postConst && branch.fals != null) {
                    execute.visitLabel(branch.fals);
                }
            }
        }

        return null;
    }

    @Override
    public Void visitConditional(final ConditionalContext ctx) {
        final ExpressionMetadata condemd = adapter.getExpressionMetadata(ctx);
        final Branch branch = adapter.getBranch(ctx);

        final ExpressionContext expr0 = ctx.expression(0);
        final ExpressionContext expr1 = ctx.expression(1);
        final ExpressionContext expr2 = ctx.expression(2);

        final Branch local = adapter.markBranch(ctx, expr0);
        local.fals = new Label();
        local.end = new Label();

        visit(expr0);
        visit(expr1);
        execute.visitJumpInsn(Opcodes.GOTO, local.end);
        execute.visitLabel(local.fals);
        visit(expr2);
        execute.visitLabel(local.end);

        if (branch == null) {
            caster.checkWriteCast(execute, condemd);
        }

        return null;
    }

    @Override
    public Void visitAssignment(final AssignmentContext ctx) {
        final External external = adapter.getExternal(ctx);
        external.setWriter(this, execute);
        external.write(ctx);

        return null;
    }

    @Override
    public Void visitExtstart(ExtstartContext ctx) {
        throw new UnsupportedOperationException(); // TODO: message
    }

    @Override
    public Void visitExtprec(final ExtprecContext ctx) {
        throw new UnsupportedOperationException(); // TODO: message
    }

    @Override
    public Void visitExtcast(final ExtcastContext ctx) {
        throw new UnsupportedOperationException(); // TODO: message
    }

    @Override
    public Void visitExtbrace(final ExtbraceContext ctx) {
        throw new UnsupportedOperationException(); // TODO: message
    }

    @Override
    public Void visitExtdot(final ExtdotContext ctx) {
        throw new UnsupportedOperationException(); // TODO: message
    }

    @Override
    public Void visitExttype(final ExttypeContext ctx) {
        throw new UnsupportedOperationException(); // TODO: message
    }

    @Override
    public Void visitExtcall(final ExtcallContext ctx) {
        throw new UnsupportedOperationException(); // TODO: message
    }

    @Override
    public Void visitExtmember(final ExtmemberContext ctx) {
        throw new UnsupportedOperationException(); // TODO: message
    }

    @Override
    public Void visitArguments(final ArgumentsContext ctx) {
        throw new UnsupportedOperationException(); // TODO: message
    }

    @Override
    public Void visitIncrement(IncrementContext ctx) {
        final ExpressionMetadata incremd = adapter.getExpressionMetadata(ctx);
        final Object postConst = incremd.postConst;

        if (postConst == null) {
            writeString(incremd.preConst);
            caster.checkWriteCast(execute, incremd);
        } else {
            writeConstant(postConst);
        }

        adapter.checkWriteBranch(execute, ctx);

        return null;
    }

    void writeConstant(final Object constant) {
        if (constant instanceof Number) {
            writeNumeric(constant);
        } else if (constant instanceof Character) {
            writeNumeric((int)(char)constant);
        } else if (constant instanceof String) {
            writeString(constant);
        } else if (constant instanceof Boolean) {
            writeBoolean(constant);
        } else if (constant != null) {
            throw new IllegalStateException(); // TODO: message
        }
    }

    private void writeNumeric(final Object numeric) {
        if (numeric instanceof Double) {
            final long bits = Double.doubleToLongBits((Double)numeric);

            if (bits == 0L) {
                execute.visitInsn(Opcodes.DCONST_0);
            } else if (bits == 0x3ff0000000000000L) {
                execute.visitInsn(Opcodes.DCONST_1);
            } else {
                execute.visitLdcInsn(numeric);
            }
        } else if (numeric instanceof Float) {
            final int bits = Float.floatToIntBits((Float)numeric);

            if (bits == 0L) {
                execute.visitInsn(Opcodes.FCONST_0);
            } else if (bits == 0x3f800000) {
                execute.visitInsn(Opcodes.FCONST_1);
            } else if (bits == 0x40000000) {
                execute.visitInsn(Opcodes.FCONST_2);
            } else {
                execute.visitLdcInsn(numeric);
            }
        } else if (numeric instanceof Long) {
            final long value = (long)numeric;

            if (value == 0L) {
                execute.visitInsn(Opcodes.LCONST_0);
            } else if (value == 1L) {
                execute.visitInsn(Opcodes.LCONST_1);
            } else {
                execute.visitLdcInsn(value);
            }
        } else if (numeric instanceof Number) {
            final int value = ((Number)numeric).intValue();

            if (value == -1) {
                execute.visitInsn(Opcodes.ICONST_M1);
            } else if (value == 0) {
                execute.visitInsn(Opcodes.ICONST_0);
            } else if (value == 1) {
                execute.visitInsn(Opcodes.ICONST_1);
            } else if (value == 2) {
                execute.visitInsn(Opcodes.ICONST_2);
            } else if (value == 3) {
                execute.visitInsn(Opcodes.ICONST_3);
            } else if (value == 4) {
                execute.visitInsn(Opcodes.ICONST_4);
            } else if (value == 5) {
                execute.visitInsn(Opcodes.ICONST_5);
            } else if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) {
                execute.visitIntInsn(Opcodes.BIPUSH, value);
            } else if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) {
                execute.visitIntInsn(Opcodes.SIPUSH, value);
            } else {
                execute.visitLdcInsn(value);
            }
        } else {
            throw new IllegalStateException(); // TODO: message
        }
    }

    private void writeString(final Object constant) {
        if (constant instanceof String) {
            execute.visitLdcInsn(constant);
        } else {
            throw new IllegalStateException(); // TODO: message
        }
    }

    private void writeBoolean(final Object constant) {
        if (constant instanceof Boolean) {
            boolean value = (boolean)constant;

            if (value) {
                execute.visitInsn(Opcodes.ICONST_1);
            } else {
                execute.visitInsn(Opcodes.ICONST_0);
            }
        } else {
            throw new IllegalStateException(); // TODO: message
        }
    }

    void writeNewStrings() {
        execute.visitTypeInsn(Opcodes.NEW, "java/lang/StringBuilder");
        execute.visitInsn(Opcodes.DUP);
        execute.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false);
    }

    void writeAppendStrings(final TypeMetadata metadata) {
        final String internal = "java/lang/StringBuilder";
        final String builder = "Ljava/lang/StringBuilder;";
        final String string = "(Ljava/lang/String;)" + builder;
        final String object = "Ljava/lang/Object;)" + builder;

        switch (metadata) {
            case BOOL:   execute.visitMethodInsn(Opcodes.INVOKEVIRTUAL, internal, "append", "(Z)" + builder, false); break;
            case BYTE:   execute.visitMethodInsn(Opcodes.INVOKEVIRTUAL, internal, "append", "(B)" + builder, false); break;
            case SHORT:  execute.visitMethodInsn(Opcodes.INVOKEVIRTUAL, internal, "append", "(S)" + builder, false); break;
            case CHAR:   execute.visitMethodInsn(Opcodes.INVOKEVIRTUAL, internal, "append", "(C)" + builder, false); break;
            case INT:    execute.visitMethodInsn(Opcodes.INVOKEVIRTUAL, internal, "append", "(I)" + builder, false); break;
            case LONG:   execute.visitMethodInsn(Opcodes.INVOKEVIRTUAL, internal, "append", "(J)" + builder, false); break;
            case FLOAT:  execute.visitMethodInsn(Opcodes.INVOKEVIRTUAL, internal, "append", "(F)" + builder, false); break;
            case DOUBLE: execute.visitMethodInsn(Opcodes.INVOKEVIRTUAL, internal, "append", "(D)" + builder, false); break;
            case STRING: execute.visitMethodInsn(Opcodes.INVOKEVIRTUAL, internal, "append", string, false);          break;
            case OBJECT: execute.visitMethodInsn(Opcodes.INVOKEVIRTUAL, internal, "append", object, false);          break;
            default:
                throw new IllegalStateException(); // TODO: message
        }
    }

    void writeToStrings() {
        execute.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false);
    }

    void writeBinaryInstruction(final TypeMetadata metadata, final int token) {
        switch (metadata) {
            case INT:
                switch (token) {
                    case MUL:   execute.visitInsn(Opcodes.IMUL);  break;
                    case DIV:   execute.visitInsn(Opcodes.IDIV);  break;
                    case REM:   execute.visitInsn(Opcodes.IREM);  break;
                    case ADD:   execute.visitInsn(Opcodes.IADD);  break;
                    case SUB:   execute.visitInsn(Opcodes.ISUB);  break;
                    case LSH:   execute.visitInsn(Opcodes.ISHL);  break;
                    case USH:   execute.visitInsn(Opcodes.IUSHR); break;
                    case RSH:   execute.visitInsn(Opcodes.ISHR);  break;
                    case BWAND: execute.visitInsn(Opcodes.IAND);  break;
                    case BWXOR: execute.visitInsn(Opcodes.IXOR);  break;
                    case BWOR:  execute.visitInsn(Opcodes.IOR);   break;
                    default:
                        throw new IllegalStateException(); // TODO: message
                }

                break;
            case LONG:
                switch (token) {
                    case MUL:   execute.visitInsn(Opcodes.LMUL);  break;
                    case DIV:   execute.visitInsn(Opcodes.LDIV);  break;
                    case REM:   execute.visitInsn(Opcodes.LREM);  break;
                    case ADD:   execute.visitInsn(Opcodes.LADD);  break;
                    case SUB:   execute.visitInsn(Opcodes.LSUB);  break;
                    case LSH:   execute.visitInsn(Opcodes.LSHL);  break;
                    case USH:   execute.visitInsn(Opcodes.LUSHR); break;
                    case RSH:   execute.visitInsn(Opcodes.LSHR);  break;
                    case BWAND: execute.visitInsn(Opcodes.LAND);  break;
                    case BWXOR: execute.visitInsn(Opcodes.LXOR);  break;
                    case BWOR:  execute.visitInsn(Opcodes.LOR);   break;
                    default:
                        throw new IllegalStateException(); // TODO: message
                }

                break;
            case FLOAT:
                switch (token) {
                    case MUL: execute.visitInsn(Opcodes.FMUL); break;
                    case DIV: execute.visitInsn(Opcodes.FDIV); break;
                    case REM: execute.visitInsn(Opcodes.FREM); break;
                    case ADD: execute.visitInsn(Opcodes.FADD); break;
                    case SUB: execute.visitInsn(Opcodes.FSUB); break;
                    default:
                        throw new IllegalStateException(); // TODO: message
                }

                break;
            case DOUBLE:
                switch (token) {
                    case MUL: execute.visitInsn(Opcodes.DMUL); break;
                    case DIV: execute.visitInsn(Opcodes.DDIV); break;
                    case REM: execute.visitInsn(Opcodes.DREM); break;
                    case ADD: execute.visitInsn(Opcodes.DADD); break;
                    case SUB: execute.visitInsn(Opcodes.DSUB); break;
                    default:
                        throw new IllegalStateException(); // TODO: message
                }

                break;
            default:
                throw new IllegalStateException(); // TODO: message
        }
    }

    private void writeEnd() {
        writer.visitEnd();
    }

    private byte[] getBytes() {
        return writer.toByteArray();
    }
}