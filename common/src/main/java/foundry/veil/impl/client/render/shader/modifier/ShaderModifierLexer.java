package foundry.veil.impl.client.render.shader.modifier;

import com.mojang.brigadier.StringReader;
import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Ocelot
 */
@ApiStatus.Internal
public final class ShaderModifierLexer {

    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\t");

    public static Token[] createTokens(String input) {
        StringReader reader = new StringReader(WHITESPACE_PATTERN.matcher(input).replaceAll(""));
        List<Token> tokens = new ArrayList<>();

        while (reader.canRead()) {
            while (reader.canRead() && reader.peek() != '\n' && Character.isWhitespace(reader.peek())) {
                reader.skip();
            }

            Token token = getToken(reader);
            if (token != null) {
                tokens.add(token);
                continue;
            }

            throw new IllegalStateException("Unknown Token");
        }

        return tokens.toArray(Token[]::new);
    }

    private static Token getToken(StringReader reader) {
        String remaining = reader.getRemaining();
        for (TokenType type : TokenType.values()) {
            Matcher matcher = type.pattern.matcher(remaining);
            if (matcher.find() && matcher.start() == 0) {
                reader.setCursor(reader.getCursor() + matcher.end());
                return new Token(type, remaining.substring(0, matcher.end()));
            }
        }

        return null;
    }

    public record Token(TokenType type, String value) {
        public String lowercaseValue() {
            return this.value.toLowerCase(Locale.ROOT);
        }

        @Override
        public String toString() {
            return this.type + "[" + this.value + "]";
        }
    }

    public enum TokenType {
        COMMENT("\\/\\/.+"),
        VERSION("#version"),
        PRIORITY("#priority"),
        INCLUDE("#include"),
        REPLACE("#replace"),
        GET_ATTRIBUTE("GET_ATTRIBUTE"),
        OUTPUT("OUTPUT"),
        UNIFORM("UNIFORM"),
        FUNCTION("FUNCTION"),
        HEAD("HEAD"),
        TAIL("TAIL"),
        DEFINITION("(int|ivec2|ivec3|ivec4|uint|uvec2|uvec3|uvec4|float|vec2|vec3|vec4|double|dvec2|dvec3|dvec4|mat2|mat2x3|mat2x4|mat3|mat3x2|mat3x4|mat4|mat4x2|mat4x3)\\s+(\\w+)\\s*;"),
        NUMERAL("-?\\d+"),
        ALPHANUMERIC("\\w+"),
        COLON(":"),
        NAMESPACE("[a-z0-9_\\-.]+"),
        PATH("[a-z0-9_\\-./]+"),
        LEFT_BRACKET("\\["),
        RIGHT_BRACKET("\\]"),
        LEFT_PARENTHESIS("\\("),
        RIGHT_PARENTHESIS("\\)"),
        NEWLINE("\n"),
        CODE(".+");

        private final Pattern pattern;

        TokenType(String regex) {
            this.pattern = Pattern.compile(regex);
        }

        public Pattern getPattern() {
            return this.pattern;
        }

        public boolean isValidLocation() {
            return this == ALPHANUMERIC || this == NAMESPACE || this == PATH || this == COLON;
        }

        public boolean isCommand() {
            return this == GET_ATTRIBUTE || this == OUTPUT || this == UNIFORM || this == FUNCTION;
        }

        public boolean isWhitespace() {
            return this == COMMENT || this == NEWLINE;
        }
    }
}
