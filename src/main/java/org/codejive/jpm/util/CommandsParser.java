package org.codejive.jpm.util;

import java.util.ArrayList;
import java.util.List;

/** Parser for shell commands */
public class CommandsParser {
    private final String input;

    private int position;
    private String currentToken;

    public CommandsParser(String input) {
        this.input = input;
        this.position = 0;
        nextToken();
    }

    // Parse the entire input according to the grammar
    public Commands parse() {
        try {
            return parseCommands();
        } catch (RuntimeException e) {
            return null;
        }
    }

    // commands ::= <elem>+
    private Commands parseCommands() {
        List<Node> elements = new ArrayList<>();

        while (currentToken != null) {
            elements.add(parseElem());
        }

        return new Commands(elements);
    }

    // elem ::= <group> | <separator> | <command>
    private Node parseElem() {
        if (currentToken == null) {
            throw new RuntimeException("Unexpected end of input");
        }

        if (currentToken.equals("(")) {
            return parseGroup();
        } else if (currentToken.equals(";")
                || currentToken.equals("&&")
                || currentToken.equals("||")) {
            return parseSeparator();
        } else {
            return parseCommand();
        }
    }

    // separator ::= ';' | '&&' | '||'
    private Separator parseSeparator() {
        String type = currentToken;
        nextToken();
        return new Separator(type);
    }

    // group ::= '(' <commands> ')'
    private Group parseGroup() {
        consume("(");
        Commands commands = parseCommands();
        if (currentToken == null) {
            throw new RuntimeException("Unexpected end of input, missing ')'");
        }
        consume(")");
        return new Group(commands);
    }

    // <command> ::= <word>+
    private Command parseCommand() {
        List<String> words = new ArrayList<>();

        while (currentToken != null
                && !currentToken.equals("(")
                && !currentToken.equals(")")
                && !currentToken.equals(";")
                && !currentToken.equals("&&")
                && !currentToken.equals("||")) {
            words.add(currentToken);
            nextToken();
        }

        if (words.isEmpty()) {
            throw new RuntimeException("Empty command");
        }

        return new Command(words);
    }

    private void consume(String expected) {
        if (currentToken.equals(expected)) {
            nextToken();
        } else {
            throw new RuntimeException(
                    "Expected '" + expected + "' but found '" + currentToken + "'");
        }
    }

    private void nextToken() {
        // Skip whitespace
        while (position < input.length() && Character.isWhitespace(input.charAt(position))) {
            position++;
        }

        if (position >= input.length()) {
            currentToken = null;
            return;
        }

        char c = input.charAt(position);

        // Handle special characters
        if (c == '(' || c == ')' || c == ';') {
            currentToken = String.valueOf(c);
            position++;
        } else if (c == '&' && position + 1 < input.length() && input.charAt(position + 1) == '&') {
            currentToken = "&&";
            position += 2;
        } else if (c == '|' && position + 1 < input.length() && input.charAt(position + 1) == '|') {
            currentToken = "||";
            position += 2;
        } else {
            // Parse a word token
            StringBuilder sb = new StringBuilder();
            boolean inQuotes = false;
            char quoteChar = 0;

            while (position < input.length()) {
                c = input.charAt(position);
                if (!inQuotes) {
                    if (Character.isWhitespace(c)
                            || c == '('
                            || c == ')'
                            || c == ';'
                            || (c == '&'
                                    && position + 1 < input.length()
                                    && input.charAt(position + 1) == '&')
                            || (c == '|'
                                    && position + 1 < input.length()
                                    && input.charAt(position + 1) == '|')) {
                        break;
                    } else if (c == '"' || c == '\'') {
                        inQuotes = true;
                        quoteChar = c;
                    }
                } else {
                    if (c == quoteChar) {
                        // Add closing quote character to preserve it
                        inQuotes = false;
                    }
                }
                sb.append(c);
                position++;
            }

            currentToken = sb.toString();
        }
    }

    // AST node types
    public interface Node {}

    public static class Commands implements Node {
        public final List<Node> elements;

        public Commands(List<Node> elements) {
            this.elements = elements;
        }
    }

    public static class Command implements Node {
        public final List<String> words;

        public Command(List<String> words) {
            this.words = words;
        }
    }

    public static class Group implements Node {
        public final Commands commands;

        public Group(Commands commands) {
            this.commands = commands;
        }
    }

    public static class Separator implements Node {
        public final String type;

        public Separator(String type) {
            this.type = type;
        }
    }
}
