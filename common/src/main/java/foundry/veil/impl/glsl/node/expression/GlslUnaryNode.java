package foundry.veil.impl.glsl.node.expression;

import foundry.veil.impl.glsl.node.GlslNode;

public class GlslUnaryNode implements GlslNode {

    private GlslNode expression;
    private Operand operand;

    public GlslUnaryNode(GlslNode expression, Operand operand) {
        this.expression = expression;
        this.operand = operand;
    }

    public GlslNode getExpression() {
        return this.expression;
    }

    public Operand getOperand() {
        return this.operand;
    }

    public GlslUnaryNode setExpression(GlslNode expression) {
        this.expression = expression;
        return this;
    }

    public GlslUnaryNode setOperand(Operand operand) {
        this.operand = operand;
        return this;
    }

    @Override
    public String toString() {
        return "GlslUnaryNode{expression=" + this.expression + ", " + "operand=" + this.operand + '}';
    }

    @Override
    public String getSourceString() {
        return switch (this.operand) {
            case PRE_INCREMENT,
                 PRE_DECREMENT,
                 PLUS,
                 DASH,
                 BANG,
                 TILDE -> this.operand.getDelimiter() + this.expression.getSourceString();
            case POST_INCREMENT, POST_DECREMENT -> this.expression.getSourceString() + this.operand.getDelimiter();
        };
    }

    public enum Operand {
        PRE_INCREMENT("++"),
        PRE_DECREMENT("--"),
        POST_INCREMENT("++"),
        POST_DECREMENT("--"),
        PLUS("+"),
        DASH("-"),
        BANG("!"),
        TILDE("~");

        private final String delimiter;

        Operand(String delimiter) {
            this.delimiter = delimiter;
        }

        public String getDelimiter() {
            return this.delimiter;
        }
    }
}
