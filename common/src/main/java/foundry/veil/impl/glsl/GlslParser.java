package foundry.veil.impl.glsl;

import foundry.veil.impl.glsl.grammar.*;
import foundry.veil.impl.glsl.node.GlslEmptyNode;
import foundry.veil.impl.glsl.node.GlslNode;
import foundry.veil.impl.glsl.node.GlslTree;
import foundry.veil.impl.glsl.node.branch.*;
import foundry.veil.impl.glsl.node.expression.*;
import foundry.veil.impl.glsl.node.function.GlslFunctionNode;
import foundry.veil.impl.glsl.node.function.GlslInvokeFunctionNode;
import foundry.veil.impl.glsl.node.function.GlslPrimitiveConstructorNode;
import foundry.veil.impl.glsl.node.primary.GlslBoolConstantNode;
import foundry.veil.impl.glsl.node.primary.GlslFloatConstantNode;
import foundry.veil.impl.glsl.node.primary.GlslIntConstantNode;
import foundry.veil.impl.glsl.node.primary.GlslIntFormat;
import foundry.veil.impl.glsl.node.variable.*;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public final class GlslParser {

    public static GlslTree parse(String input) throws GlslSyntaxException {
        return parse(GlslLexer.createTokens(input));
    }

    public static GlslTree parse(GlslLexer.Token[] tokens) throws GlslSyntaxException {
        GlslTokenReader reader = new GlslTokenReader(tokens);

        GlslVersion version = new GlslVersion(110, true);

        // Try to parse version statements
        GlslLexer.Token token = reader.peek();
        if (token.type() == GlslLexer.TokenType.DIRECTIVE && token.value().startsWith("#version ")) {
            reader.skip();
            String[] parts = token.value().substring(9).split(" +", 2);
            try {
                int ver = Integer.parseInt(parts[0]);
                boolean core = parts.length == 1 || parts[1].equals("core");
                version.setVersion(ver);
                version.setCore(core);
            } catch (NumberFormatException e) {
                throw reader.error("Invalid Version: " + token.value() + ". " + e.getMessage());
            }
        }

        List<String> directives = new ArrayList<>();
        List<GlslNode> body = new ArrayList<>();
        while (reader.canRead()) {
            if (reader.tryConsume(GlslLexer.TokenType.DIRECTIVE)) {
                directives.add(reader.peek(-1).value());
                continue;
            }

            GlslFunctionNode functionDefinition = parseFunctionDefinition(reader);
            if (functionDefinition != null) {
                body.add(functionDefinition);
                continue;
            }

            GlslNode declaration = parseDeclaration(reader);
            if (declaration != null) {
                body.add(declaration);
                continue;
            }

            if (reader.tryConsume(GlslLexer.TokenType.SEMICOLON)) {
                continue;
            }

            reader.throwError();
        }

        return new GlslTree(version, body, directives);
    }

    public static GlslNode parseExpression(String input) throws GlslSyntaxException {
        return parseExpression(GlslLexer.createTokens(input));
    }

    public static GlslNode parseExpression(GlslLexer.Token[] tokens) throws GlslSyntaxException {
        GlslTokenReader reader = new GlslTokenReader(tokens);
        GlslNode expression = parseConditionalExpression(reader);
        if (expression == null) {
            reader.throwError();
        }
        while (reader.canRead()) {
            if (reader.peek().type() != GlslLexer.TokenType.SEMICOLON) {
                break;
            }
            reader.skip();
        }
        if (reader.canRead()) {
            throw reader.error("Too many tokens provided");
        }
        return expression;
    }

    public static List<GlslNode> parseExpressionList(String input) throws GlslSyntaxException {
        return parseExpressionList(GlslLexer.createTokens(input));
    }

    public static List<GlslNode> parseExpressionList(GlslLexer.Token[] tokens) throws GlslSyntaxException {
        GlslTokenReader reader = new GlslTokenReader(tokens);
        List<GlslNode> expressions = parseStatementList(reader);
        if (reader.canRead()) {
            throw reader.error("Too many tokens provided");
        }
        return expressions;
    }

    private static @Nullable GlslNode parsePrimaryExpression(GlslTokenReader reader) {
        // IDENTIFIER
        // INTCONSTANT
        // UINTCONSTANT
        // FLOATCONSTANT
        // BOOLCONSTANT
        // DOUBLECONSTANT
        // LEFT_PAREN condition RIGHT_PAREN

        if (reader.tryConsume(GlslLexer.TokenType.IDENTIFIER)) {
            String variableName = reader.peek(-1).value();
            return new GlslVariableNode(variableName);
        }
        if (reader.tryConsume(GlslLexer.TokenType.INTEGER_DECIMAL_CONSTANT)) {
            return new GlslIntConstantNode(GlslIntFormat.DECIMAL, true, Integer.parseUnsignedInt(reader.peek(-1).value(), 10));
        }
        if (reader.tryConsume(GlslLexer.TokenType.INTEGER_HEXADECIMAL_CONSTANT)) {
            return new GlslIntConstantNode(GlslIntFormat.HEXADECIMAL, true, Integer.parseUnsignedInt(reader.peek(-1).value(), 16));
        }
        if (reader.tryConsume(GlslLexer.TokenType.INTEGER_OCTAL_CONSTANT)) {
            return new GlslIntConstantNode(GlslIntFormat.OCTAL, true, Integer.parseUnsignedInt(reader.peek(-1).value(), 8));
        }
        if (reader.tryConsume(GlslLexer.TokenType.UINTEGER_DECIMAL_CONSTANT)) {
            return new GlslIntConstantNode(GlslIntFormat.DECIMAL, false, Integer.parseUnsignedInt(reader.peek(-1).value(), 10));
        }
        if (reader.tryConsume(GlslLexer.TokenType.UINTEGER_HEXADECIMAL_CONSTANT)) {
            return new GlslIntConstantNode(GlslIntFormat.HEXADECIMAL, false, Integer.parseUnsignedInt(reader.peek(-1).value(), 16));
        }
        if (reader.tryConsume(GlslLexer.TokenType.UINTEGER_OCTAL_CONSTANT)) {
            return new GlslIntConstantNode(GlslIntFormat.OCTAL, false, Integer.parseUnsignedInt(reader.peek(-1).value(), 8));
        }
        if (reader.tryConsume(GlslLexer.TokenType.FLOATING_CONSTANT)) {
            return new GlslFloatConstantNode(Float.parseFloat(reader.peek(-1).value()));
        }
        if (reader.tryConsume(GlslLexer.TokenType.BOOL_CONSTANT)) {
            return new GlslBoolConstantNode(Boolean.parseBoolean(reader.peek(-1).value()));
        }

        int cursor = reader.getCursor();
        if (reader.tryConsume(GlslLexer.TokenType.LEFT_PAREN)) {
            GlslNode condition = parseCondition(reader);
            if (condition != null && reader.tryConsume(GlslLexer.TokenType.RIGHT_PAREN)) {
                return condition;
            }
        }

        reader.setCursor(cursor);
        return null;
    }

    private static @Nullable GlslNode parsePostfixExpression(boolean allowFunction, GlslTokenReader reader) {
        int cursor = reader.getCursor();

//        primary_expression
//        primary_expression LEFT_BRACKET integer_expression RIGHT_BRACKET
//        primary_expression DOT FIELD_SELECTION
//        primary_expression INC_OP
//        primary_expression DEC_OP
//        function_call LEFT_BRACKET integer_expression RIGHT_BRACKET
//        function_call
//        function_call DOT FIELD_SELECTION
//        function_call INC_OP
//        function_call DEC_OP

        if (allowFunction) {
            // function_call
            GlslNode functionCall = parseFunctionCallGeneric(reader);
            if (functionCall != null) {
                // function_call LEFT_BRACKET integer_expression RIGHT_BRACKET
                int functionCursor = reader.getCursor();
                if (reader.tryConsume(GlslLexer.TokenType.LEFT_BRACKET)) {
                    GlslNode integerExpression = parseIntegerExpression(reader);
                    if (integerExpression != null && reader.tryConsume(GlslLexer.TokenType.RIGHT_BRACKET)) {
                        functionCall = new GlslArrayNode(functionCall, integerExpression);
                        functionCursor = reader.getCursor();
                    }
                }
                reader.setCursor(functionCursor);

                // function_call DOT FIELD_SELECTION
                if (reader.tryConsume(GlslLexer.TokenType.DOT, GlslLexer.TokenType.IDENTIFIER)) {
                    StringBuilder fieldSelection = new StringBuilder(reader.peek(-1).value());
                    while (reader.tryConsume(GlslLexer.TokenType.DOT, GlslLexer.TokenType.IDENTIFIER)) {
                        fieldSelection.append('.').append(reader.peek(-1).value());
                    }
                    return new GlslFieldNode(functionCall, fieldSelection.toString());
                }
                reader.setCursor(functionCursor);

                // function_call INC_OP
                if (reader.tryConsume(GlslLexer.TokenType.INC_OP)) {
                    return new GlslUnaryNode(functionCall, GlslUnaryNode.Operand.POST_INCREMENT);
                }
                reader.setCursor(functionCursor);

                // function_call DEC_OP
                if (reader.tryConsume(GlslLexer.TokenType.DEC_OP)) {
                    return new GlslUnaryNode(functionCall, GlslUnaryNode.Operand.POST_DECREMENT);
                }
                reader.setCursor(functionCursor);

                return functionCall;
            }
            reader.setCursor(cursor);
        }

        // primary_expression
        GlslNode primaryExpression = parsePrimaryExpression(reader);
        if (primaryExpression != null) {
            // primary_expression LEFT_BRACKET integer_expression RIGHT_BRACKET
            int expressionCursor = reader.getCursor();
            if (reader.tryConsume(GlslLexer.TokenType.LEFT_BRACKET)) {
                GlslNode integerExpression = parseIntegerExpression(reader);
                if (integerExpression != null && reader.tryConsume(GlslLexer.TokenType.RIGHT_BRACKET)) {
                    primaryExpression = new GlslArrayNode(primaryExpression, integerExpression);
                    expressionCursor = reader.getCursor();
                }
            }
            reader.setCursor(expressionCursor);

            // primary_expression DOT FIELD_SELECTION
            if (reader.tryConsume(GlslLexer.TokenType.DOT, GlslLexer.TokenType.IDENTIFIER)) {
                StringBuilder fieldSelection = new StringBuilder(reader.peek(-1).value());
                while (reader.tryConsume(GlslLexer.TokenType.DOT, GlslLexer.TokenType.IDENTIFIER)) {
                    fieldSelection.append('.').append(reader.peek(-1).value());
                }
                return new GlslFieldNode(primaryExpression, fieldSelection.toString());
            }
            reader.setCursor(expressionCursor);
            // primary_expression INC_OP
            if (reader.tryConsume(GlslLexer.TokenType.INC_OP)) {
                return new GlslUnaryNode(primaryExpression, GlslUnaryNode.Operand.POST_INCREMENT);
            }
            reader.setCursor(expressionCursor);

            // primary_expression DEC_OP
            if (reader.tryConsume(GlslLexer.TokenType.DEC_OP)) {
                return new GlslUnaryNode(primaryExpression, GlslUnaryNode.Operand.POST_DECREMENT);
            }
            reader.setCursor(expressionCursor);

            return primaryExpression;
        }

        reader.setCursor(cursor);
        return null;
    }

    private static @Nullable GlslNode parseIntegerExpression(GlslTokenReader reader) {
        return parseExpression(reader);
    }

    private static @Nullable GlslNode parseFunctionCallGeneric(GlslTokenReader reader) {
        GlslNode functionCallHeader = parseFunctionCallHeader(reader);
        if (functionCallHeader == null) {
            return null;
        }

        int cursor = reader.getCursor();

        // function_call_header assignment_expression RIGHT_PAREN
        // function_call_header_with_parameters COMMA assignment_expression RIGHT_PAREN
        int parameterCursor = reader.getCursor();
        List<GlslNode> parameters = new ArrayList<>();
        while (reader.canRead()) {
            GlslNode parameter = parseAssignmentExpression(reader);
            if (parameter == null) {
                reader.setCursor(parameterCursor);
                break;
            }

            parameters.add(parameter);
            parameterCursor = reader.getCursor();

            if (!reader.tryConsume(GlslLexer.TokenType.COMMA)) {
                break;
            }
        }

        if (reader.tryConsume(GlslLexer.TokenType.RIGHT_PAREN)) {
            return new GlslInvokeFunctionNode(functionCallHeader, parameters);
        }
        reader.setCursor(cursor);

        // function_call_header VOID RIGHT_PAREN
        if (reader.tryConsume(GlslLexer.TokenType.VOID, GlslLexer.TokenType.RIGHT_PAREN)) {
            return new GlslInvokeFunctionNode(functionCallHeader, Collections.emptyList());
        }
        reader.setCursor(cursor);

        // function_call_header RIGHT_PAREN
        if (reader.tryConsume(GlslLexer.TokenType.RIGHT_PAREN)) {
            return new GlslInvokeFunctionNode(functionCallHeader, Collections.emptyList());
        }

        reader.markError("Expected ')'");
        reader.setCursor(cursor);
        return null;
    }

    private static @Nullable GlslNode parseFunctionCallHeader(GlslTokenReader reader) {
        int cursor = reader.getCursor();
        // type_specifier LEFT_PAREN
        GlslTypeSpecifier typeSpecifier = parseTypeSpecifier(reader);
        if (typeSpecifier != null && !typeSpecifier.isNamed() && reader.tryConsume(GlslLexer.TokenType.LEFT_PAREN)) {
            return new GlslPrimitiveConstructorNode(typeSpecifier);
        }
        reader.setCursor(cursor);

        // postfix_expression LEFT_PAREN
        GlslNode expression = parsePostfixExpression(false, reader);
        if (expression != null && reader.tryConsume(GlslLexer.TokenType.LEFT_PAREN)) {
            return expression;
        }
        reader.setCursor(cursor);
        return null;
    }

    private static @Nullable GlslNode parseUnaryExpression(GlslTokenReader reader) {
        // unary_operator unary_expression

        GlslNode expression = parsePostfixExpression(true, reader);
        if (expression != null) {
            return expression;
        }

        int cursor = reader.getCursor();

        // INC_OP unary_expression
        if (reader.tryConsume(GlslLexer.TokenType.INC_OP)) {
            GlslNode right = parseUnaryExpression(reader);
            if (right != null) {
                return new GlslUnaryNode(right, GlslUnaryNode.Operand.PRE_INCREMENT);
            }
        }
        reader.setCursor(cursor);

        // DEC_OP unary_expression
        if (reader.tryConsume(GlslLexer.TokenType.DEC_OP)) {
            GlslNode right = parseUnaryExpression(reader);
            if (right != null) {
                return new GlslUnaryNode(right, GlslUnaryNode.Operand.PRE_DECREMENT);
            }
        }
        reader.setCursor(cursor);

        // postfix_expression
        if (reader.canRead()) {
            GlslUnaryNode.Operand operator = reader.peek().type().asUnaryOperator();
            if (operator != null) {
                reader.skip();
                GlslNode right = parseUnaryExpression(reader);
                if (right != null) {
                    return new GlslUnaryNode(right, operator);
                }
            }
        }

        reader.setCursor(cursor);
        return null;
    }

    private static @Nullable GlslNode parseSimpleExpression(GlslTokenReader reader, Function<GlslTokenReader, GlslNode> parser, GlslLexer.TokenType operator, Function<List<GlslNode>, GlslNode> join) {
        int cursor = reader.getCursor();
        List<GlslNode> expressions = new ArrayList<>();
        while (reader.canRead()) {
            GlslNode expression = parser.apply(reader);
            if (expression == null) {
                reader.setCursor(cursor);
                break;
            }

            expressions.add(expression);
            cursor = reader.getCursor();

            if (!reader.tryConsume(operator)) {
                break;
            }
        }

        if (expressions.isEmpty()) {
            return null;
        }
        if (expressions.size() == 1) {
            return expressions.get(0);
        }
        return join.apply(expressions);
    }

    private static @Nullable GlslNode parseMultiplicativeExpression(GlslTokenReader reader) {
        // unary_expression
        // multiplicative_expression STAR unary_expression
        // multiplicative_expression SLASH unary_expression
        // multiplicative_expression PERCENT unary_expression

        GlslNode left = parseUnaryExpression(reader);
        if (left == null) {
            return null;
        }

        while (reader.canRead()) {
            int cursor = reader.getCursor();
            if (reader.tryConsume(GlslLexer.TokenType.STAR)) {
                GlslNode right = parseUnaryExpression(reader);
                if (right == null) {
                    reader.setCursor(cursor);
                    return left;
                }
                left = new GlslOperationNode(left, right, GlslOperationNode.Operand.MULTIPLY);
                continue;
            }
            if (reader.tryConsume(GlslLexer.TokenType.SLASH)) {
                GlslNode right = parseUnaryExpression(reader);
                if (right == null) {
                    reader.setCursor(cursor);
                    return left;
                }
                left = new GlslOperationNode(left, right, GlslOperationNode.Operand.DIVIDE);
                continue;
            }
            if (reader.tryConsume(GlslLexer.TokenType.PERCENT)) {
                GlslNode right = parseUnaryExpression(reader);
                if (right == null) {
                    reader.setCursor(cursor);
                    return left;
                }
                left = new GlslOperationNode(left, right, GlslOperationNode.Operand.MODULO);
                continue;
            }
            break;
        }

        return left;
    }

    private static @Nullable GlslNode parseAdditiveExpression(GlslTokenReader reader) {
        // multiplicative_expression
        // additive_expression PLUS multiplicative_expression
        // additive_expression DASH multiplicative_expression

        GlslNode left = parseMultiplicativeExpression(reader);
        if (left == null) {
            return null;
        }

        while (reader.canRead()) {
            int cursor = reader.getCursor();
            if (reader.tryConsume(GlslLexer.TokenType.PLUS)) {
                GlslNode right = parseMultiplicativeExpression(reader);
                if (right == null) {
                    reader.setCursor(cursor);
                    return left;
                }
                left = new GlslOperationNode(left, right, GlslOperationNode.Operand.ADD);
                continue;
            }
            if (reader.tryConsume(GlslLexer.TokenType.DASH)) {
                GlslNode right = parseMultiplicativeExpression(reader);
                if (right == null) {
                    reader.setCursor(cursor);
                    return left;
                }
                left = new GlslOperationNode(left, right, GlslOperationNode.Operand.SUBTRACT);
                continue;
            }
            break;
        }

        return left;
    }

    private static @Nullable GlslNode parseShiftExpression(GlslTokenReader reader) {
        // additive_expression
        // shift_expression LEFT_OP additive_expression
        // shift_expression RIGHT_OP additive_expression

        GlslNode left = parseAdditiveExpression(reader);
        if (left == null) {
            return null;
        }

        while (reader.canRead()) {
            int cursor = reader.getCursor();
            if (reader.tryConsume(GlslLexer.TokenType.LEFT_OP)) {
                GlslNode right = parseAdditiveExpression(reader);
                if (right == null) {
                    reader.setCursor(cursor);
                    return left;
                }
                left = new GlslOperationNode(left, right, GlslOperationNode.Operand.LEFT_SHIFT);
                continue;
            }
            if (reader.tryConsume(GlslLexer.TokenType.RIGHT_OP)) {
                GlslNode right = parseAdditiveExpression(reader);
                if (right == null) {
                    reader.setCursor(cursor);
                    return left;
                }
                left = new GlslOperationNode(left, right, GlslOperationNode.Operand.RIGHT_SHIFT);
                continue;
            }
            break;
        }

        return left;
    }

    private static @Nullable GlslNode parseRelationalExpression(GlslTokenReader reader) {
        // shift_expression
        // relational_expression LEFT_ANGLE shift_expression
        // relational_expression RIGHT_ANGLE shift_expression
        // relational_expression LE_OP shift_expression
        // relational_expression GE_OP shift_expression

        GlslNode left = parseShiftExpression(reader);
        if (left == null) {
            return null;
        }

        while (reader.canRead()) {
            int cursor = reader.getCursor();
            if (reader.tryConsume(GlslLexer.TokenType.LEFT_ANGLE)) {
                GlslNode right = parseShiftExpression(reader);
                if (right == null) {
                    reader.setCursor(cursor);
                    return left;
                }
                left = new GlslCompareNode(left, right, GlslCompareNode.Operand.LESS);
                continue;
            }
            if (reader.tryConsume(GlslLexer.TokenType.RIGHT_ANGLE)) {
                GlslNode right = parseShiftExpression(reader);
                if (right == null) {
                    reader.setCursor(cursor);
                    return left;
                }
                left = new GlslCompareNode(left, right, GlslCompareNode.Operand.GREATER);
                continue;
            }
            if (reader.tryConsume(GlslLexer.TokenType.LE_OP)) {
                GlslNode right = parseShiftExpression(reader);
                if (right == null) {
                    reader.setCursor(cursor);
                    return left;
                }
                left = new GlslCompareNode(left, right, GlslCompareNode.Operand.LEQUAL);
                continue;
            }
            if (reader.tryConsume(GlslLexer.TokenType.GE_OP)) {
                GlslNode right = parseShiftExpression(reader);
                if (right == null) {
                    reader.setCursor(cursor);
                    return left;
                }
                left = new GlslCompareNode(left, right, GlslCompareNode.Operand.GEQUAL);
                continue;
            }
            break;
        }

        return left;
    }

    private static @Nullable GlslNode parseEqualityExpression(GlslTokenReader reader) {
        // relational_expression
        // equality_expression EQ_OP relational_expression
        // equality_expression NE_OP relational_expression

        GlslNode left = parseRelationalExpression(reader);
        if (left == null) {
            return null;
        }

        while (reader.canRead()) {
            int cursor = reader.getCursor();
            if (reader.tryConsume(GlslLexer.TokenType.EQ_OP)) {
                GlslNode right = parseRelationalExpression(reader);
                if (right == null) {
                    reader.setCursor(cursor);
                    return left;
                }
                left = new GlslCompareNode(left, right, GlslCompareNode.Operand.EQUAL);
                continue;
            }
            if (reader.tryConsume(GlslLexer.TokenType.NE_OP)) {
                GlslNode right = parseRelationalExpression(reader);
                if (right == null) {
                    reader.setCursor(cursor);
                    return left;
                }
                left = new GlslCompareNode(left, right, GlslCompareNode.Operand.NOT_EQUAL);
                continue;
            }
            break;
        }

        return left;
    }

    private static @Nullable GlslNode parseAndExpression(GlslTokenReader reader) {
        // equality_expression
        // and_expression AMPERSAND equality_expression
        return parseSimpleExpression(reader, GlslParser::parseEqualityExpression, GlslLexer.TokenType.AMPERSAND, GlslAndNode::new);
    }

    private static @Nullable GlslNode parseExclusiveOrExpression(GlslTokenReader reader) {
        // and_expression
        // exclusive_or_expression CARET and_expression
        return parseSimpleExpression(reader, GlslParser::parseAndExpression, GlslLexer.TokenType.CARET, GlslExclusiveOrNode::new);
    }

    private static @Nullable GlslNode parseInclusiveOrExpression(GlslTokenReader reader) {
        // exclusive_or_expression
        // inclusive_or_expression VERTICAL_BAR exclusive_or_expression
        return parseSimpleExpression(reader, GlslParser::parseExclusiveOrExpression, GlslLexer.TokenType.VERTICAL_BAR, GlslLogicalAndNode::new);
    }

    private static @Nullable GlslNode parseLogicalAndExpression(GlslTokenReader reader) {
        // inclusive_or_expression
        // logical_and_expression AND_OP inclusive_or_expression
        return parseSimpleExpression(reader, GlslParser::parseInclusiveOrExpression, GlslLexer.TokenType.AND_OP, GlslLogicalAndNode::new);
    }

    private static @Nullable GlslNode parseLogicalXorExpression(GlslTokenReader reader) {
        // logical_and_expression
        // logical_xor_expression XOR_OP logical_and_expression
        return parseSimpleExpression(reader, GlslParser::parseLogicalAndExpression, GlslLexer.TokenType.XOR_OP, GlslLogicalXorNode::new);
    }

    private static @Nullable GlslNode parseLogicalOrExpression(GlslTokenReader reader) {
        // logical_xor_expression
        // logical_or_expression OR_OP logical_xor_expression
        return parseSimpleExpression(reader, GlslParser::parseLogicalXorExpression, GlslLexer.TokenType.OR_OP, GlslLogicalOrNode::new);
    }

    private static @Nullable GlslNode parseConditionalExpression(GlslTokenReader reader) {
        // logical_or_expression
        GlslNode logicalOr = parseLogicalOrExpression(reader);
        if (logicalOr == null) {
            return null;
        }

        // logical_or_expression QUESTION condition COLON assignment_expression
        int cursor = reader.getCursor();
        if (reader.tryConsume(GlslLexer.TokenType.QUESTION)) {
            GlslNode first = parseExpression(reader);
            if (first != null && reader.tryConsume(GlslLexer.TokenType.COLON)) {
                GlslNode branch = parseAssignmentExpression(reader);
                if (branch != null) {
                    return new GlslConditionalNode(logicalOr, first, branch);
                }
            }
        }

        reader.setCursor(cursor);
        return logicalOr;
    }

    private static @Nullable GlslNode parseAssignmentExpression(GlslTokenReader reader) {
        // unary_expression assignment_operator assignment_expression
        int cursor = reader.getCursor();
        GlslNode unaryExpression = parseUnaryExpression(reader);
        if (reader.canRead()) {
            GlslAssignmentNode.Operand assignmentOperator = reader.peek().type().asAssignmentOperator();
            if (assignmentOperator != null) {
                reader.skip();
                GlslNode right = parseAssignmentExpression(reader);
                return new GlslAssignmentNode(unaryExpression, right, assignmentOperator);
            }
        }
        reader.setCursor(cursor);

        // conditional_expression
        return parseConditionalExpression(reader);
    }

    private static @Nullable GlslNode parseExpression(GlslTokenReader reader) {
        int cursor = reader.getCursor();

        // assignment_expression
        // expression COMMA assignment_expression
        List<GlslNode> expressions = new ArrayList<>();
        while (reader.canRead()) {
            GlslNode expression = parseAssignmentExpression(reader);
            if (expression == null) {
                reader.setCursor(cursor);
                break;
            }

            expressions.add(expression);
            cursor = reader.getCursor();

            if (!reader.tryConsume(GlslLexer.TokenType.COMMA)) {
                break;
            }
        }

        if (expressions.isEmpty()) {
            return null;
        }

        return GlslNode.compound(expressions);
    }

    private static @Nullable GlslNode parseDeclaration(GlslTokenReader reader) {
        // function_prototype SEMICOLON
        // init_declarator_list SEMICOLON
        // PRECISION precision_qualifier type_specifier SEMICOLON
        // type_qualifier IDENTIFIER LEFT_BRACE struct_declaration_list RIGHT_BRACE SEMICOLON
        // type_qualifier IDENTIFIER LEFT_BRACE struct_declaration_list RIGHT_BRACE IDENTIFIER SEMICOLON
        // type_qualifier IDENTIFIER LEFT_BRACE struct_declaration_list RIGHT_BRACE IDENTIFIER array_specifier SEMICOLON
        // type_qualifier SEMICOLON
        // type_qualifier IDENTIFIER SEMICOLON
        // type_qualifier IDENTIFIER identifier_list SEMICOLON

        int cursor = reader.getCursor();

        // function_prototype SEMICOLON
        GlslFunctionHeader functionPrototype = parseFunctionPrototype(reader);
        if (functionPrototype != null) {
            if (reader.tryConsume(GlslLexer.TokenType.SEMICOLON)) {
                return new GlslFunctionNode(functionPrototype, null);
            }
            reader.setCursor(cursor);
        }

        // init_declarator_list SEMICOLON
        GlslNode initDeclaratorList = parseInitDeclaratorList(reader);
        if (initDeclaratorList != null) {
            if (reader.tryConsume(GlslLexer.TokenType.SEMICOLON)) {
                return initDeclaratorList;
            }
            reader.setCursor(cursor);
        }

        // PRECISION precision_qualifier type_specifier SEMICOLON
        if (reader.tryConsume(GlslLexer.TokenType.PRECISION)) {
            GlslTypeQualifier.Precision precisionQualifier = parsePrecisionQualifier(reader);
            if (precisionQualifier != null) {
                GlslTypeSpecifier typeSpecifier = parseTypeSpecifier(reader);
                if (typeSpecifier != null && reader.tryConsume(GlslLexer.TokenType.SEMICOLON)) {
                    return new GlslPrecisionNode(precisionQualifier, typeSpecifier);
                }
            }
            reader.setCursor(cursor);
        }

        List<GlslTypeQualifier> typeQualifier = parseTypeQualifiers(reader);
        if (typeQualifier == null) {
            return null;
        }

        // FIXME
        cursor = reader.getCursor();
        if (reader.tryConsume(GlslLexer.TokenType.IDENTIFIER, GlslLexer.TokenType.LEFT_BRACE)) {
            String identifier = reader.peek(-2).value();

            List<GlslStructField> structFields = parseStructDeclarationList(reader);
            if (structFields != null) {
                if (reader.tryConsume(GlslLexer.TokenType.RIGHT_BRACE)) {
                    GlslSpecifiedType structSpecifier = new GlslSpecifiedType(new GlslStructSpecifier(identifier, structFields), typeQualifier);

                    // type_qualifier IDENTIFIER LEFT_BRACE struct_declaration_list RIGHT_BRACE SEMICOLON
                    if (reader.tryConsume(GlslLexer.TokenType.SEMICOLON)) {
                        return new GlslStructNode(structSpecifier);
                    }

                    if (reader.tryConsume(GlslLexer.TokenType.IDENTIFIER)) {
                        String label = reader.peek(-1).value();

                        // type_qualifier IDENTIFIER LEFT_BRACE struct_declaration_list RIGHT_BRACE IDENTIFIER SEMICOLON
                        if (reader.tryConsume(GlslLexer.TokenType.SEMICOLON)) {
                            return new GlslNewNode(structSpecifier, label, null);
                        }

                        // type_qualifier IDENTIFIER LEFT_BRACE struct_declaration_list RIGHT_BRACE IDENTIFIER array_specifier SEMICOLON
                        GlslSpecifiedType arraySpecifier = parseArraySpecifier(reader, structSpecifier);
                        if (arraySpecifier != null && reader.tryConsume(GlslLexer.TokenType.SEMICOLON)) {
                            return new GlslNewNode(arraySpecifier, label, null);
                        }
                    }
                }
                reader.markError("Expected '}'");
            }
        }
        reader.setCursor(cursor);

        // type_qualifier SEMICOLON
        if (reader.tryConsume(GlslLexer.TokenType.SEMICOLON)) {
            return new GlslDeclaration(typeQualifier, Collections.emptyList());
        }

        // type_qualifier IDENTIFIER SEMICOLON
        // type_qualifier IDENTIFIER identifier_list SEMICOLON
        List<String> identifiers = parseIdentifierList(reader);
        if (identifiers == null) {
            reader.markError("Expected ';'");
            reader.setCursor(cursor);
            return null;
        }

        if (!reader.tryConsume(GlslLexer.TokenType.SEMICOLON)) {
            reader.markError("Expected ';'");
            reader.setCursor(cursor);
            return null;
        }

        return new GlslDeclaration(typeQualifier, identifiers);
    }

    private static @Nullable List<String> parseIdentifierList(GlslTokenReader reader) {
        List<String> identifiers = new ArrayList<>();
        int cursor = reader.getCursor();
        while (reader.canRead()) {
            if (!reader.tryConsume(GlslLexer.TokenType.IDENTIFIER)) {
                reader.setCursor(cursor);
                break;
            }

            identifiers.add(reader.peek(-1).value());
            cursor = reader.getCursor();
            if (!reader.tryConsume(GlslLexer.TokenType.COMMA)) {
                break;
            }
        }

        if (identifiers.isEmpty()) {
            return null;
        }

        return identifiers;
    }

    private static @Nullable GlslFunctionHeader parseFunctionPrototype(GlslTokenReader reader) {
        int cursor = reader.getCursor();

        // fully_specified_type IDENTIFIER LEFT_PAREN RIGHT_PAREN
        GlslSpecifiedType fullySpecifiedType = parseFullySpecifiedType(reader);
        if (fullySpecifiedType != null && reader.tryConsume(GlslLexer.TokenType.IDENTIFIER, GlslLexer.TokenType.LEFT_PAREN, GlslLexer.TokenType.RIGHT_PAREN)) {
            String name = reader.peek(-3).value();
            return new GlslFunctionHeader(name, fullySpecifiedType, new ArrayList<>());
        }
        reader.setCursor(cursor);

        // function_header_with_parameters RIGHT_PAREN
        GlslFunctionHeader functionHeaderWithParameters = parseFunctionHeaderWithParameters(reader);
        if (functionHeaderWithParameters != null) {
            return functionHeaderWithParameters;
        }

        // fully_specified_type IDENTIFIER LEFT_PAREN parameter_declaration
        fullySpecifiedType = parseFullySpecifiedType(reader);
        if (fullySpecifiedType != null && reader.tryConsume(GlslLexer.TokenType.IDENTIFIER, GlslLexer.TokenType.LEFT_PAREN)) {
            String name = reader.peek(-2).value();
            GlslParameterDeclaration parameterDeclaration = parseParameterDeclaration(reader);
            if (parameterDeclaration != null && reader.tryConsume(GlslLexer.TokenType.RIGHT_PAREN)) {
                return new GlslFunctionHeader(name, fullySpecifiedType, Collections.singletonList(parameterDeclaration));
            }
        }
        reader.setCursor(cursor);

        // function_header_with_parameters COMMA parameter_declaration
        functionHeaderWithParameters = parseFunctionHeaderWithParameters(reader);
        if (functionHeaderWithParameters != null && reader.tryConsume(GlslLexer.TokenType.COMMA)) {
            GlslParameterDeclaration parameterDeclaration = parseParameterDeclaration(reader);
            if (parameterDeclaration != null && reader.tryConsume(GlslLexer.TokenType.RIGHT_PAREN)) {
                List<GlslParameterDeclaration> parameters = functionHeaderWithParameters.getParameters();
                parameters.add(parameterDeclaration);
                return functionHeaderWithParameters;
            }
        }
        reader.setCursor(cursor);
        return null;
    }

    private static List<GlslParameterDeclaration> parseParameterList(GlslTokenReader reader) {
        List<GlslParameterDeclaration> parameters = new ArrayList<>();
        int cursor = reader.getCursor();
        while (reader.canRead()) {
            GlslParameterDeclaration parameterDeclaration = parseParameterDeclaration(reader);
            if (parameterDeclaration == null) {
                reader.setCursor(cursor);
                break;
            }

            parameters.add(parameterDeclaration);
            cursor = reader.getCursor();
            if (!reader.tryConsume(GlslLexer.TokenType.COMMA)) {
                break;
            }
        }

        return parameters;
    }

    private static @Nullable GlslFunctionHeader parseFunctionHeaderWithParameters(GlslTokenReader reader) {
        // fully_specified_type IDENTIFIER LEFT_PAREN parameter_declaration
        // function_header_with_parameters COMMA parameter_declaration

        int cursor = reader.getCursor();
        GlslSpecifiedType fullySpecifiedType = parseFullySpecifiedType(reader);
        if (fullySpecifiedType != null && reader.tryConsume(GlslLexer.TokenType.IDENTIFIER, GlslLexer.TokenType.LEFT_PAREN)) {
            String name = reader.peek(-2).value();
            List<GlslParameterDeclaration> parameters = parseParameterList(reader);
            if (reader.tryConsume(GlslLexer.TokenType.RIGHT_PAREN)) {
                return new GlslFunctionHeader(name, fullySpecifiedType, parameters);
            }
        }

        reader.setCursor(cursor);
        return null;
    }

    private static @Nullable GlslParameterDeclaration parseParameterDeclarator(GlslTokenReader reader) {
        // type_specifier IDENTIFIER
        // type_specifier IDENTIFIER array_specifier

        int cursor = reader.getCursor();
        GlslTypeSpecifier typeSpecifier = parseTypeSpecifier(reader);
        if (typeSpecifier == null) {
            return null;
        }

        if (!reader.tryConsume(GlslLexer.TokenType.IDENTIFIER)) {
            reader.setCursor(cursor);
            return null;
        }

        String name = reader.peek(-1).value();
        GlslTypeSpecifier arraySpecifier = parseArraySpecifier(reader, typeSpecifier);
        return new GlslParameterDeclaration(name, Objects.requireNonNullElse(arraySpecifier, typeSpecifier));
    }

    private static @Nullable GlslParameterDeclaration parseParameterDeclaration(GlslTokenReader reader) {
        int cursor = reader.getCursor();

        // type_qualifier
        List<GlslTypeQualifier> typeQualifiers = parseTypeQualifiers(reader);
        if (typeQualifiers != null) {
            // type_qualifier parameter_declarator
            GlslParameterDeclaration parameterDeclarator = parseParameterDeclarator(reader);
            if (parameterDeclarator != null) {
                return parameterDeclarator.setQualifiers(typeQualifiers);
            }

            // type_qualifier parameter_type_specifier
            GlslTypeSpecifier parameterTypeSpecifier = parseTypeSpecifier(reader);
            if (parameterTypeSpecifier != null) {
                return new GlslParameterDeclaration(null, new GlslSpecifiedType(parameterTypeSpecifier, typeQualifiers));
            }
        }
        reader.setCursor(cursor);

        // parameter_declarator
        GlslParameterDeclaration parameterDeclarator = parseParameterDeclarator(reader);
        if (parameterDeclarator != null) {
            return parameterDeclarator;
        }

        // parameter_type_specifier
        GlslTypeSpecifier parameterTypeSpecifier = parseTypeSpecifier(reader);
        if (parameterTypeSpecifier != null) {
            return new GlslParameterDeclaration(null, parameterTypeSpecifier);
        }

        return null;
    }

    private static @Nullable GlslNode parseInitDeclaratorList(GlslTokenReader reader) {
        // single_declaration
        // init_declarator_list COMMA IDENTIFIER
        // init_declarator_list COMMA IDENTIFIER array_specifier
        // init_declarator_list COMMA IDENTIFIER array_specifier EQUAL initializer
        // init_declarator_list COMMA IDENTIFIER EQUAL initializer

        List<GlslNode> initDeclaratorList = new ArrayList<>();
        while (reader.canRead()) {
            GlslNode singleDeclaration = parseSingleDeclaration(reader);
            if (singleDeclaration == null) {
                break;
            }

            initDeclaratorList.add(singleDeclaration);
        }

        if (initDeclaratorList.isEmpty()) {
            return null;
        }
        return GlslNode.compound(initDeclaratorList);
    }

    private static @Nullable GlslNode parseSingleDeclaration(GlslTokenReader reader) {
        // fully_specified_type
        // fully_specified_type IDENTIFIER
        // fully_specified_type IDENTIFIER array_specifier
        // fully_specified_type IDENTIFIER array_specifier EQUAL initializer
        // fully_specified_type IDENTIFIER EQUAL initializer

        int cursor = reader.getCursor();
        GlslSpecifiedType fullySpecifiedType = parseFullySpecifiedType(reader);
        if (fullySpecifiedType == null) {
            return null;
        }

        if (!reader.tryConsume(GlslLexer.TokenType.IDENTIFIER)) {
            reader.setCursor(cursor);
            return null;
        }

        cursor = reader.getCursor();
        String name = reader.peek(-1).value();

        GlslSpecifiedType arraySpecifier = parseArraySpecifier(reader, fullySpecifiedType);
        if (arraySpecifier != null) {
            if (!reader.tryConsume(GlslLexer.TokenType.EQUAL)) {
                // fully_specified_type IDENTIFIER array_specifier
                return new GlslNewNode(arraySpecifier, name, null);
            }

            GlslNode initializer = parseInitializer(reader);
            if (initializer != null) {
                // fully_specified_type IDENTIFIER array_specifier EQUAL initializer
                return new GlslNewNode(arraySpecifier, name, initializer);
            }
        }
        reader.setCursor(cursor);

        if (reader.tryConsume(GlslLexer.TokenType.EQUAL)) {
            GlslNode initializer = parseInitializer(reader);
            if (initializer != null) {
                // fully_specified_type IDENTIFIER EQUAL initializer
                return new GlslNewNode(fullySpecifiedType, name, initializer);
            }
        }

        // fully_specified_type IDENTIFIER
        reader.setCursor(cursor);
        return new GlslNewNode(fullySpecifiedType, name, null);
    }

    private static @Nullable GlslSpecifiedType parseFullySpecifiedType(GlslTokenReader reader) {
        // type_specifier
        // type_qualifier type_specifier

        // type_specifier
        GlslTypeSpecifier typeSpecifier = parseTypeSpecifier(reader);
        if (typeSpecifier != null) {
            return new GlslSpecifiedType(typeSpecifier);
        }

        // type_qualifier type_specifier
        int cursor = reader.getCursor();
        List<GlslTypeQualifier> typeQualifiers = parseTypeQualifiers(reader);
        if (typeQualifiers != null) {
            typeSpecifier = parseTypeSpecifier(reader);
            if (typeSpecifier != null) {
                return new GlslSpecifiedType(typeSpecifier, typeQualifiers);
            }
        }

        reader.setCursor(cursor);
        return null;
    }

    private static @Nullable GlslNode parseInvariantQualifier(GlslTokenReader reader) {
        // INVARIANT
        return null; // TODO
    }

    private static @Nullable GlslTypeQualifier.Interpolation parseInterpolationQualifier(GlslTokenReader reader) {
        // SMOOTH
        // FLAT
        // NOPERSPECTIVE

        GlslLexer.TokenType type = reader.peekType(0);
        GlslTypeQualifier.Interpolation interpolationQualifier = type != null ? type.asInterpolationQualifier() : null;
        if (interpolationQualifier != null) {
            reader.skip();
            return interpolationQualifier;
        }
        return null;
    }

    private static @Nullable GlslTypeQualifier parseLayoutQualifier(GlslTokenReader reader) {
        // LAYOUT LEFT_PAREN layout_qualifier_id_list RIGHT_PAREN
        if (!reader.tryConsume(GlslLexer.TokenType.LAYOUT, GlslLexer.TokenType.LEFT_PAREN)) {
            return null;
        }

        // layout_qualifier_id_list
        int layoutCursor = reader.getCursor();
        List<GlslTypeQualifier.LayoutId> layoutQualifierIds = new ArrayList<>();
        while (reader.canRead()) {
            GlslTypeQualifier.LayoutId qualifier = null;
            if (reader.tryConsume(GlslLexer.TokenType.IDENTIFIER)) {
                String identifier = reader.peek(-1).value();
                GlslNode expression = null;

                int cursor = reader.getCursor();
                if (reader.tryConsume(GlslLexer.TokenType.EQUAL)) {
                    expression = parseConditionalExpression(reader);
                    if (expression == null) {
                        reader.setCursor(cursor);
                    }
                }

                qualifier = GlslTypeQualifier.identifierLayoutId(identifier, expression);
            }

            if (qualifier == null && reader.tryConsume(GlslLexer.TokenType.SHARED)) {
                qualifier = GlslTypeQualifier.sharedLayoutId();
            }

            if (qualifier == null) {
                break;
            }

            layoutQualifierIds.add(qualifier);
            if (!reader.tryConsume(GlslLexer.TokenType.COMMA)) {
                break;
            }
        }

        // RIGHT_PAREN
        if (!reader.tryConsume(GlslLexer.TokenType.RIGHT_PAREN)) {
            reader.markError("Expected ')'");
            reader.setCursor(layoutCursor);
            return null;
        }

        return GlslTypeQualifier.layout(layoutQualifierIds.toArray(GlslTypeQualifier.LayoutId[]::new));
    }

    private static @Nullable List<GlslTypeQualifier> parseTypeQualifiers(GlslTokenReader reader) {
        // single_type_qualifier
        // type_qualifier single_type_qualifier

        List<GlslTypeQualifier> typeQualifiers = new ArrayList<>();
        while (reader.canRead()) {
            // storage_qualifier
            GlslTypeQualifier storageQualifier = parseStorageQualifier(reader);
            if (storageQualifier != null) {
                typeQualifiers.add(storageQualifier);
                continue;
            }

            // layout_qualifier
            GlslTypeQualifier layoutQualifier = parseLayoutQualifier(reader);
            if (layoutQualifier != null) {
                typeQualifiers.add(layoutQualifier);
                continue;
            }

            // precision_qualifier
            GlslTypeQualifier precisionQualifier = parsePrecisionQualifier(reader);
            if (precisionQualifier != null) {
                typeQualifiers.add(precisionQualifier);
                continue;
            }

            // interpolation_qualifier
            GlslTypeQualifier interpolationQualifier = parseInterpolationQualifier(reader);
            if (interpolationQualifier != null) {
                typeQualifiers.add(interpolationQualifier);
                continue;
            }

            // invariant_qualifier
            if (reader.tryConsume(GlslLexer.TokenType.INVARIANT)) {
                typeQualifiers.add(GlslTypeQualifier.Invariant.INVARIANT);
                continue;
            }

            // precise_qualifier
            if (reader.tryConsume(GlslLexer.TokenType.PRECISE)) {
                typeQualifiers.add(GlslTypeQualifier.Precise.PRECISE);
                continue;
            }

            break;
        }

        return typeQualifiers.isEmpty() ? null : typeQualifiers;
    }

    private static @Nullable GlslTypeQualifier parseStorageQualifier(GlslTokenReader reader) {
        GlslTypeQualifier.StorageType storageQualifier = reader.peek().type().asStorageQualifier();
        if (storageQualifier != null) {
            reader.skip();
            return storageQualifier;
        }

        // SUBROUTINE LEFT_PAREN type_name_list RIGHT_PAREN
        int cursor = reader.getCursor();
        if (reader.tryConsume(GlslLexer.TokenType.SUBROUTINE)) {
            if (reader.tryConsume(GlslLexer.TokenType.LEFT_PAREN)) {
                List<String> typeNames = new ArrayList<>();
                while (reader.canRead()) {
                    if (!reader.tryConsume(GlslLexer.TokenType.IDENTIFIER)) {
                        break;
                    }

                    typeNames.add(reader.peek(-1).value());
                    if (!reader.tryConsume(GlslLexer.TokenType.COMMA)) {
                        break;
                    }
                }
                if (!reader.tryConsume(GlslLexer.TokenType.RIGHT_PAREN)) {
                    reader.markError("Expected ')'");
                    reader.setCursor(cursor);
                    return null;
                }
                return GlslTypeQualifier.storage(typeNames.toArray(String[]::new));
            }
            return GlslTypeQualifier.storage(new String[0]);
        }

        reader.setCursor(cursor);
        return null;
    }

    private static @Nullable GlslNode parseTypeNameList(GlslTokenReader reader) {
        // TYPE_NAME
        // type_name_list COMMA TYPE_NAME
        return null; // TODO
    }

    private static @Nullable GlslTypeSpecifier parseTypeSpecifier(GlslTokenReader reader) {
        // type_specifier_nonarray
        // type_specifier_nonarray array_specifier

        if (!reader.canRead()) {
            return null;
        }

        // type_specifier_nonarray : VOID | FLOAT | DOUBLE | INT | UINT | BOOL | VEC2 | VEC3 | VEC4 | DVEC2 | DVEC3 | DVEC4 | BVEC2 | BVEC3 | BVEC4 | IVEC2 | IVEC3 | IVEC4 | UVEC2 | UVEC3 | UVEC4 | MAT2 | MAT3 | MAT4 | MAT2X2 | MAT2X3 | MAT2X4 | MAT3X2 | MAT3X3 | MAT3X4 | MAT4X2 | MAT4X3 | MAT4X4 | DMAT2 | DMAT3 | DMAT4 | DMAT2X2 | DMAT2X3 | DMAT2X4 | DMAT3X2 | DMAT3X3 | DMAT3X4 | DMAT4X2 | DMAT4X3 | DMAT4X4 | ATOMIC_UINT | SAMPLER2D | SAMPLER3D | SAMPLERCUBE | SAMPLER2DSHADOW | SAMPLERCUBESHADOW | SAMPLER2DARRAY | SAMPLER2DARRAYSHADOW | SAMPLERCUBEARRAY | SAMPLERCUBEARRAYSHADOW | ISAMPLER2D | ISAMPLER3D | ISAMPLERCUBE | ISAMPLER2DARRAY | ISAMPLERCUBEARRAY | USAMPLER2D | USAMPLER3D | USAMPLERCUBE | USAMPLER2DARRAY | USAMPLERCUBEARRAY | SAMPLER1D | SAMPLER1DSHADOW | SAMPLER1DARRAY | SAMPLER1DARRAYSHADOW | ISAMPLER1D | ISAMPLER1DARRAY | USAMPLER1D | USAMPLER1DARRAY | SAMPLER2DRECT | SAMPLER2DRECTSHADOW | ISAMPLER2DRECT | USAMPLER2DRECT | SAMPLERBUFFER | ISAMPLERBUFFER | USAMPLERBUFFER | SAMPLER2DMS | ISAMPLER2DMS | USAMPLER2DMS | SAMPLER2DMSARRAY | ISAMPLER2DMSARRAY | USAMPLER2DMSARRAY | IMAGE2D | IIMAGE2D | UIMAGE2D | IMAGE3D | IIMAGE3D | UIMAGE3D | IMAGECUBE | IIMAGECUBE | UIMAGECUBE | IMAGEBUFFER | IIMAGEBUFFER | UIMAGEBUFFER | IMAGE1D | IIMAGE1D | UIMAGE1D | IMAGE1DARRAY | IIMAGE1DARRAY | UIMAGE1DARRAY | IMAGE2DRECT | IIMAGE2DRECT | UIMAGE2DRECT | IMAGE2DARRAY | IIMAGE2DARRAY | UIMAGE2DARRAY | IMAGECUBEARRAY | IIMAGECUBEARRAY | UIMAGECUBEARRAY | IMAGE2DMS | IIMAGE2DMS | UIMAGE2DMS | IMAGE2DMSARRAY | IIMAGE2DMSARRAY | UIMAGE2DMSARRAY | struct_specifier | TYPE_NAME
        GlslTypeSpecifier typeSpecifier;
        GlslLexer.Token token = reader.peek();
        GlslTypeSpecifier.BuiltinType type = token.type().asBuiltinType();
        if (type != null) {
            typeSpecifier = type;
            reader.skip();
        } else {
            GlslStructSpecifier structSpecifier = parseStructSpecifier(reader);
            if (structSpecifier != null) {
                typeSpecifier = structSpecifier;
            } else {
                if (token.type() == GlslLexer.TokenType.IDENTIFIER) {
                    reader.skip();
                    typeSpecifier = GlslTypeSpecifier.named(token.value());
                } else {
                    return null;
                }
            }
        }

        // type_specifier_nonarray array_specifier
        GlslTypeSpecifier arraySpecifier = parseArraySpecifier(reader, typeSpecifier);
        if (arraySpecifier != null) {
            return arraySpecifier;
        }

        return typeSpecifier;
    }

    private static @Nullable GlslSpecifiedType parseArraySpecifier(GlslTokenReader reader, GlslSpecifiedType type) {
        GlslTypeSpecifier arraySpecifier = parseArraySpecifier(reader, type.getSpecifier());
        if (arraySpecifier != null) {
            return new GlslSpecifiedType(arraySpecifier, type.getQualifiers());
        }

        return null;
    }

    private static @Nullable GlslTypeSpecifier parseArraySpecifier(GlslTokenReader reader, GlslTypeSpecifier type) {
        int cursor = reader.getCursor();

        if (reader.tryConsume(GlslLexer.TokenType.LEFT_BRACKET)) {
            // LEFT_BRACKET RIGHT_BRACKET
            if (reader.tryConsume(GlslLexer.TokenType.RIGHT_BRACKET)) {
                return GlslTypeSpecifier.array(type, null);
            }

            // LEFT_BRACKET conditional_expression RIGHT_BRACKET
            GlslNode conditionalExpression = parseConditionalExpression(reader);
            if (conditionalExpression != null && reader.tryConsume(GlslLexer.TokenType.RIGHT_BRACKET)) {
                return GlslTypeSpecifier.array(type, conditionalExpression);
            }

            reader.setCursor(cursor);
        }

        // Impossible ???
        // array_specifier LEFT_BRACKET RIGHT_BRACKET
        // array_specifier LEFT_BRACKET conditional_expression RIGHT_BRACKET

        return null;
    }

    private static @Nullable GlslTypeQualifier.Precision parsePrecisionQualifier(GlslTokenReader reader) {
        // HIGH_PRECISION
        // MEDIUM_PRECISION
        // LOW_PRECISION

        GlslLexer.TokenType type = reader.peekType(0);
        GlslTypeQualifier.Precision precisionQualifier = type != null ? type.asPrecisionQualifier() : null;
        if (precisionQualifier != null) {
            reader.skip();
            return precisionQualifier;
        }
        return null;
    }

    private static @Nullable GlslStructSpecifier parseStructSpecifier(GlslTokenReader reader) {
        // STRUCT IDENTIFIER LEFT_BRACE struct_declaration_list RIGHT_BRACE
        // STRUCT LEFT_BRACE struct_declaration_list RIGHT_BRACE

        int cursor = reader.getCursor();
        if (!reader.tryConsume(GlslLexer.TokenType.STRUCT)) {
            return null;
        }

        String name = null;
        if (reader.tryConsume(GlslLexer.TokenType.IDENTIFIER)) {
            name = reader.peek(-1).value();
        }

        if (!reader.tryConsume(GlslLexer.TokenType.LEFT_BRACE)) {
            reader.setCursor(cursor);
            return null;
        }

        List<GlslStructField> fields = parseStructDeclarationList(reader);
        if (fields == null) {
            return null;
        }

        if (!reader.tryConsume(GlslLexer.TokenType.RIGHT_BRACE)) {
            reader.markError("Expected '}'");
            reader.setCursor(cursor);
            return null;
        }

        return new GlslStructSpecifier(name, fields);
    }

    private static @Nullable List<GlslStructField> parseStructDeclarationList(GlslTokenReader reader) {
        // struct_declaration
        // struct_declaration_list struct_declaration

        List<GlslStructField> declarations = new ArrayList<>();
        while (reader.canRead()) {
            List<GlslStructField> fields = parseStructDeclaration(reader);
            if (fields == null) {
                break;
            }

            declarations.addAll(fields);
        }

        if (declarations.isEmpty()) {
            return null;
        }

        return declarations;
    }

    private static @Nullable List<GlslStructField> parseStructDeclaration(GlslTokenReader reader) {
        // type_specifier struct_declarator_list SEMICOLON
        // type_qualifier type_specifier struct_declarator_list SEMICOLON

        int cursor = reader.getCursor();
        GlslSpecifiedType fullySpecifiedType = parseFullySpecifiedType(reader);
        if (fullySpecifiedType == null) {
            return null;
        }

        List<GlslStructField> structDeclaration = parseStructDeclaratorList(fullySpecifiedType, reader);
        if (structDeclaration == null) {
            return null;
        }

        if (!reader.tryConsume(GlslLexer.TokenType.SEMICOLON)) {
            reader.markError("Expected ';'");
            reader.setCursor(cursor);
            return null;
        }

        return structDeclaration;
    }

    private static @Nullable List<GlslStructField> parseStructDeclaratorList(GlslSpecifiedType type, GlslTokenReader reader) {
        // struct_declarator
        // struct_declarator_list COMMA struct_declarator

        int cursor = reader.getCursor();
        List<GlslStructField> fields = new ArrayList<>();
        while (reader.canRead()) {
            GlslStructField field = parseStructDeclarator(type, reader);
            if (field == null) {
                reader.setCursor(cursor);
                break;
            }

            fields.add(field);
            cursor = reader.getCursor();

            if (!reader.tryConsume(GlslLexer.TokenType.COMMA)) {
                break;
            }
        }

        if (fields.isEmpty()) {
            return null;
        }

        return fields;
    }

    private static @Nullable GlslStructField parseStructDeclarator(GlslSpecifiedType type, GlslTokenReader reader) {
        if (!reader.tryConsume(GlslLexer.TokenType.IDENTIFIER)) {
            return null;
        }

        // IDENTIFIER
        // IDENTIFIER array_specifier
        String name = reader.peek(-1).value();
        GlslSpecifiedType arraySpecifier = parseArraySpecifier(reader, type);
        return new GlslStructField(Objects.requireNonNullElse(arraySpecifier, type), name);
    }

    private static @Nullable GlslNode parseInitializer(GlslTokenReader reader) {
        // assignment_expression
        GlslNode assignmentExpression = parseAssignmentExpression(reader);
        if (assignmentExpression != null) {
            return assignmentExpression;
        }

        // LEFT_BRACE initializer_list RIGHT_BRACE
        // LEFT_BRACE initializer_list COMMA RIGHT_BRACE
        int cursor = reader.getCursor();
        if (reader.tryConsume(GlslLexer.TokenType.LEFT_BRACE)) {
            List<GlslNode> initializerList = parseInitializerList(reader);
            reader.tryConsume(GlslLexer.TokenType.COMMA);
            if (reader.tryConsume(GlslLexer.TokenType.RIGHT_BRACE)) {
                return GlslNode.compound(initializerList);
            }
        }

        reader.setCursor(cursor);
        return null;
    }

    private static List<GlslNode> parseInitializerList(GlslTokenReader reader) {
        // initializer
        // initializer_list COMMA initializer

        int cursor = reader.getCursor();
        List<GlslNode> initializers = new ArrayList<>();
        while (reader.canRead()) {
            GlslNode statement = parseStatement(reader);
            if (statement == null) {
                reader.setCursor(cursor);
                break;
            }

            initializers.add(statement);
            cursor = reader.getCursor();

            if (!reader.tryConsume(GlslLexer.TokenType.COMMA)) {
                break;
            }
        }

        return initializers;
    }

    private static @Nullable GlslNode parseStatement(GlslTokenReader reader) {
        // compound_statement
        // simple_statement

        int cursor = reader.getCursor();

        // compound_statement
        GlslNode compoundStatement = parseCompoundStatement(reader);
        if (compoundStatement != null) {
            return compoundStatement;
        }
        reader.setCursor(cursor);

        // simple_statement
        GlslNode simpleStatement = parseSimpleStatement(reader);
        if (simpleStatement != null) {
            return simpleStatement;
        }
        reader.setCursor(cursor);

        return null;
    }

    private static @Nullable GlslNode parseSimpleStatement(GlslTokenReader reader) {
        // declaration_statement
        // expression_statement
        // selection_statement
        // switch_statement
        // case_label
        // iteration_statement
        // jump_statement

        int cursor = reader.getCursor();
        GlslNode statement;

        // declaration_statement -> declaration
        statement = parseDeclaration(reader);
        if (statement != null) {
            return statement;
        }
        reader.setCursor(cursor);

        // expression_statement
        statement = parseExpressionStatement(reader);
        if (statement != null) {
            return statement;
        }
        reader.setCursor(cursor);

        // selection_statement
        statement = parseSelectionStatement(reader);
        if (statement != null) {
            return statement;
        }
        reader.setCursor(cursor);

        statement = parseSwitchStatement(reader);
        if (statement != null) {
            return statement;
        }
        reader.setCursor(cursor);

        statement = parseCaseLabel(reader);
        if (statement != null) {
            return statement;
        }
        reader.setCursor(cursor);

        statement = parseIterationStatement(reader);
        if (statement != null) {
            return statement;
        }
        reader.setCursor(cursor);

        statement = parseJumpStatement(reader);
        if (statement != null) {
            return statement;
        }
        reader.setCursor(cursor);

        return null;
    }

    private static @Nullable GlslNode parseCompoundStatement(GlslTokenReader reader) {
        // LEFT_BRACE RIGHT_BRACE
        // LEFT_BRACE statement_list RIGHT_BRACE

        int cursor = reader.getCursor();
        if (!reader.tryConsume(GlslLexer.TokenType.LEFT_BRACE)) {
            reader.setCursor(cursor);
            return null;
        }

        if (reader.tryConsume(GlslLexer.TokenType.RIGHT_BRACE)) {
            return GlslEmptyNode.INSTANCE;
        }

        // statement_list
        List<GlslNode> statements = parseStatementList(reader);

        if (!reader.tryConsume(GlslLexer.TokenType.RIGHT_BRACE)) {
            reader.setCursor(cursor);
            return null;
        }

        return GlslNode.compound(statements);
    }

    private static @Nullable GlslNode parseStatementNoNewScope(GlslTokenReader reader) {
        // compound_statement_no_new_scope
        GlslNode statementNoNewScope = parseCompoundStatementNoNewScope(reader);
        if (statementNoNewScope != null) {
            return statementNoNewScope;
        }

        // simple_statement
        return parseSimpleStatement(reader);
    }

    private static @Nullable GlslNode parseCompoundStatementNoNewScope(GlslTokenReader reader) {
        // LEFT_BRACE RIGHT_BRACE
        // LEFT_BRACE statement_list RIGHT_BRACE

        int cursor = reader.getCursor();
        if (!reader.tryConsume(GlslLexer.TokenType.LEFT_BRACE)) {
            return null;
        }

        List<GlslNode> statements = parseStatementList(reader);
        if (!reader.tryConsume(GlslLexer.TokenType.RIGHT_BRACE)) {
            reader.markError("Expected '}'");
            reader.setCursor(cursor);
            return null;
        }

        return GlslNode.compound(statements);
    }

    private static List<GlslNode> parseStatementList(GlslTokenReader reader) {
        List<GlslNode> statements = new ArrayList<>();
        while (reader.canRead()) {
            GlslNode statement = parseStatement(reader);
            if (statement == null) {
                break;
            }

            statements.add(statement);
        }

        return statements;
    }

    private static @Nullable GlslNode parseExpressionStatement(GlslTokenReader reader) {
        if (reader.tryConsume(GlslLexer.TokenType.SEMICOLON)) {
            return GlslEmptyNode.INSTANCE;
        }

        int cursor = reader.getCursor();
        GlslNode condition = parseCondition(reader);
        if (condition != null && reader.tryConsume(GlslLexer.TokenType.SEMICOLON)) {
            return condition;
        }
        reader.setCursor(cursor);
        return null;
    }

    private static @Nullable GlslNode parseSelectionStatement(GlslTokenReader reader) {
        // IF LEFT_PAREN condition RIGHT_PAREN statement ELSE statement
        // IF LEFT_PAREN condition RIGHT_PAREN statement

        if (!reader.tryConsume(GlslLexer.TokenType.IF)) {
            return null;
        }

        int cursor = reader.getCursor();
        if (!reader.tryConsume(GlslLexer.TokenType.LEFT_PAREN)) {
            reader.markError("Expected '('");
            reader.setCursor(cursor);
            return null;
        }

        GlslNode expression = parseCondition(reader);
        if (expression == null) {
            reader.setCursor(cursor);
            return null;
        }

        if (!reader.tryConsume(GlslLexer.TokenType.RIGHT_PAREN)) {
            reader.markError("Expected ')'");
            reader.setCursor(cursor);
            return null;
        }

        // selection_rest_statement
        GlslNode statement = parseStatement(reader);
        if (statement == null) {
            reader.setCursor(cursor);
            return null;
        }

        cursor = reader.getCursor();
        // statement ELSE statement

        if (reader.tryConsume(GlslLexer.TokenType.ELSE)) {
            GlslNode otherStatement = parseStatement(reader);
            if (otherStatement != null) {
                return new GlslSelectionNode(expression, statement, otherStatement);
            }

            reader.setCursor(cursor);
            return null;
        }

        reader.setCursor(cursor);
        return new GlslSelectionNode(expression, statement, GlslEmptyNode.INSTANCE);
    }

    private static @Nullable GlslNode parseCondition(GlslTokenReader reader) {
        // expression
        GlslNode expression = parseExpression(reader);
        if (expression != null) {
            return expression;
        }

        // fully_specified_type IDENTIFIER EQUAL initializer
        int cursor = reader.getCursor();
        GlslSpecifiedType type = parseFullySpecifiedType(reader);
        if (type != null && reader.tryConsume(GlslLexer.TokenType.IDENTIFIER, GlslLexer.TokenType.EQUAL)) {
            String name = reader.peek(-2).value();
            GlslNode initializer = parseInitializer(reader);
            if (initializer != null) {
                return new GlslNewNode(type, name, initializer);
            }
        }

        reader.setCursor(cursor);
        return null;
    }

    private static @Nullable GlslNode parseSwitchStatement(GlslTokenReader reader) {
        // SWITCH LEFT_PAREN condition RIGHT_PAREN LEFT_BRACE switch_statement_list RIGHT_BRACE
        int cursor = reader.getCursor();
        if (reader.tryConsume(GlslLexer.TokenType.SWITCH, GlslLexer.TokenType.LEFT_PAREN)) {
            GlslNode condition = parseCondition(reader);
            if (condition != null && reader.tryConsume(GlslLexer.TokenType.RIGHT_PAREN, GlslLexer.TokenType.LEFT_BRACE)) {
                List<GlslNode> statements = parseStatementList(reader);
                if (reader.tryConsume(GlslLexer.TokenType.RIGHT_BRACE)) {
                    return new GlslSwitchNode(condition, statements);
                }
            }
        }
        reader.setCursor(cursor);
        return null;
    }

    private static @Nullable GlslCaseLabelNode parseCaseLabel(GlslTokenReader reader) {
        int cursor = reader.getCursor();

        // CASE condition COLON
        if (reader.tryConsume(GlslLexer.TokenType.CASE)) {
            GlslNode condition = parseCondition(reader);
            if (condition != null && reader.tryConsume(GlslLexer.TokenType.COLON)) {
                return new GlslCaseLabelNode(condition);
            }
        }
        reader.setCursor(cursor);

        if (reader.tryConsume(GlslLexer.TokenType.DEFAULT, GlslLexer.TokenType.COLON)) {
            return new GlslCaseLabelNode(null);
        }
        return null;
    }

    private static @Nullable GlslNode parseIterationStatement(GlslTokenReader reader) {
        int cursor = reader.getCursor();

        // WHILE LEFT_PAREN condition RIGHT_PAREN statement_no_new_scope
        if (reader.tryConsume(GlslLexer.TokenType.WHILE, GlslLexer.TokenType.LEFT_PAREN)) {
            GlslNode condition = parseCondition(reader);
            if (condition != null && reader.tryConsume(GlslLexer.TokenType.RIGHT_PAREN)) {
                GlslNode body = parseStatementNoNewScope(reader);
                if (body != null) {
                    return new WhileLoopNode(condition, body, WhileLoopNode.Type.WHILE);
                }
            }
        }
        reader.setCursor(cursor);

        // DO statement WHILE LEFT_PAREN condition RIGHT_PAREN SEMICOLON
        if (reader.tryConsume(GlslLexer.TokenType.DO)) {
            GlslNode body = parseStatement(reader);
            if (body != null && reader.tryConsume(GlslLexer.TokenType.WHILE, GlslLexer.TokenType.LEFT_PAREN)) {
                GlslNode condition = parseCondition(reader);
                if (condition != null && reader.tryConsume(GlslLexer.TokenType.RIGHT_PAREN, GlslLexer.TokenType.SEMICOLON)) {
                    return new WhileLoopNode(condition, body, WhileLoopNode.Type.DO);
                }
            }
        }
        reader.setCursor(cursor);

        // FOR LEFT_PAREN for_init_statement for_rest_statement RIGHT_PAREN statement_no_new_scope
        if (reader.tryConsume(GlslLexer.TokenType.FOR, GlslLexer.TokenType.LEFT_PAREN)) {
            GlslNode init = parseForInitStatement(reader);
            if (init != null) {
                GlslNode condition = parseConditionopt(reader);
                if (condition != null && reader.tryConsume(GlslLexer.TokenType.SEMICOLON)) {
                    GlslNode increment = parseCondition(reader);
                    if (reader.tryConsume(GlslLexer.TokenType.RIGHT_PAREN)) {
                        GlslNode body = parseStatementNoNewScope(reader);
                        if (body != null) {
                            return new ForLoopNode(init, condition, increment, body);
                        }
                    }
                }
            }
        }
        reader.setCursor(cursor);
        return null;
    }

    private static @Nullable GlslNode parseForInitStatement(GlslTokenReader reader) {
        // expression_statement
        GlslNode expressionStatement = parseExpressionStatement(reader);
        if (expressionStatement != null) {
            return expressionStatement;
        }

        // declaration_statement
        return parseDeclaration(reader);
    }

    private static GlslNode parseConditionopt(GlslTokenReader reader) {
        // condition
        GlslNode condition = parseCondition(reader);
        return condition != null ? condition : GlslEmptyNode.INSTANCE;
    }

    private static @Nullable GlslNode parseJumpStatement(GlslTokenReader reader) {
        // CONTINUE SEMICOLON
        if (reader.tryConsume(GlslLexer.TokenType.CONTINUE, GlslLexer.TokenType.SEMICOLON)) {
            return JumpNode.CONTINUE;
        }

        // BREAK SEMICOLON
        if (reader.tryConsume(GlslLexer.TokenType.BREAK, GlslLexer.TokenType.SEMICOLON)) {
            return JumpNode.BREAK;
        }

        int cursor = reader.getCursor();
        if (reader.tryConsume(GlslLexer.TokenType.RETURN)) {
            // RETURN SEMICOLON
            if (reader.tryConsume(GlslLexer.TokenType.SEMICOLON)) {
                return new GlslReturnNode(null);
            }

            // RETURN condition SEMICOLON
            GlslNode condition = parseCondition(reader);
            if (condition != null && reader.tryConsume(GlslLexer.TokenType.SEMICOLON)) {
                return new GlslReturnNode(condition);
            }
        }
        reader.setCursor(cursor);

        // DISCARD SEMICOLON
        if (reader.tryConsume(GlslLexer.TokenType.DISCARD, GlslLexer.TokenType.SEMICOLON)) {
            return JumpNode.DISCARD;
        }

        return null;
    }

    private static @Nullable GlslFunctionNode parseFunctionDefinition(GlslTokenReader reader) {
        int cursor = reader.getCursor();

        GlslFunctionHeader functionPrototype = parseFunctionPrototype(reader);
        if (functionPrototype == null) {
            return null;
        }

        GlslNode statement = parseCompoundStatementNoNewScope(reader);
        if (statement == null) {
            reader.setCursor(cursor);
            return null;
        }

        // function_prototype compound_statement_no_new_scope
        return new GlslFunctionNode(functionPrototype, statement);
    }
}
