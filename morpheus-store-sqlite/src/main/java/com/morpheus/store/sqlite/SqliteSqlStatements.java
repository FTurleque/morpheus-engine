package com.morpheus.store.sqlite;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

/**
 * Splits MORPHEUS migration resources into SQLite statements without treating semicolons in
 * literals, quoted identifiers, comments, or trigger bodies as statement terminators.
 */
final class SqliteSqlStatements {
    private SqliteSqlStatements() {
    }

    static List<String> split(String script) {
        if (script == null) throw new IllegalArgumentException("SQLite migration script is required");
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        StringBuilder token = new StringBuilder();
        Deque<String> triggerBlocks = new ArrayDeque<>();
        List<String> leadingKeywords = new ArrayList<>(4);
        boolean singleQuote = false;
        boolean doubleQuote = false;
        boolean backtick = false;
        boolean bracket = false;
        boolean lineComment = false;
        boolean blockComment = false;
        boolean trigger = false;

        for (int index = 0; index < script.length(); index++) {
            char ch = script.charAt(index);
            char next = index + 1 < script.length() ? script.charAt(index + 1) : '\0';
            current.append(ch);

            if (lineComment) {
                if (ch == '\n' || ch == '\r') lineComment = false;
                continue;
            }
            if (blockComment) {
                if (ch == '*' && next == '/') {
                    current.append(next);
                    index++;
                    blockComment = false;
                }
                continue;
            }
            if (singleQuote) {
                if (ch == '\'' && next == '\'') {
                    current.append(next);
                    index++;
                } else if (ch == '\'') {
                    singleQuote = false;
                }
                continue;
            }
            if (doubleQuote) {
                if (ch == '"' && next == '"') {
                    current.append(next);
                    index++;
                } else if (ch == '"') {
                    doubleQuote = false;
                }
                continue;
            }
            if (backtick) {
                if (ch == '`') backtick = false;
                continue;
            }
            if (bracket) {
                if (ch == ']') bracket = false;
                continue;
            }

            if (ch == '-' && next == '-') {
                flushToken(token, leadingKeywords, triggerBlocks, trigger);
                current.append(next);
                index++;
                lineComment = true;
                continue;
            }
            if (ch == '/' && next == '*') {
                flushToken(token, leadingKeywords, triggerBlocks, trigger);
                current.append(next);
                index++;
                blockComment = true;
                continue;
            }
            if (ch == '\'') {
                flushToken(token, leadingKeywords, triggerBlocks, trigger);
                singleQuote = true;
                continue;
            }
            if (ch == '"') {
                flushToken(token, leadingKeywords, triggerBlocks, trigger);
                doubleQuote = true;
                continue;
            }
            if (ch == '`') {
                flushToken(token, leadingKeywords, triggerBlocks, trigger);
                backtick = true;
                continue;
            }
            if (ch == '[') {
                flushToken(token, leadingKeywords, triggerBlocks, trigger);
                bracket = true;
                continue;
            }

            if (Character.isLetterOrDigit(ch) || ch == '_') {
                token.append(ch);
                continue;
            }

            String completed = flushToken(token, leadingKeywords, triggerBlocks, trigger);
            if (!trigger && isCreateTrigger(leadingKeywords)) {
                trigger = true;
                if (completed != null) updateTriggerBlocks(completed, triggerBlocks);
            }

            if (ch == ';') {
                if (!trigger || triggerBlocks.isEmpty()) {
                    addStatement(statements, current);
                    current.setLength(0);
                    token.setLength(0);
                    triggerBlocks.clear();
                    leadingKeywords.clear();
                    trigger = false;
                }
            }
        }

        String completed = flushToken(token, leadingKeywords, triggerBlocks, trigger);
        if (!trigger && isCreateTrigger(leadingKeywords)) {
            trigger = true;
            if (completed != null) updateTriggerBlocks(completed, triggerBlocks);
        }
        if (singleQuote || doubleQuote || backtick || bracket || blockComment) {
            throw new IllegalArgumentException("unterminated quoted value, identifier, or block comment in SQLite migration");
        }
        if (trigger && !triggerBlocks.isEmpty()) {
            throw new IllegalArgumentException("unterminated CREATE TRIGGER body in SQLite migration");
        }
        addStatement(statements, current);
        return List.copyOf(statements);
    }

    private static String flushToken(
            StringBuilder token,
            List<String> leadingKeywords,
            Deque<String> triggerBlocks,
            boolean trigger) {
        if (token.isEmpty()) return null;
        String keyword = token.toString().toUpperCase(Locale.ROOT);
        token.setLength(0);
        if (leadingKeywords.size() < 4) leadingKeywords.add(keyword);
        if (trigger) updateTriggerBlocks(keyword, triggerBlocks);
        return keyword;
    }

    private static boolean isCreateTrigger(List<String> keywords) {
        if (keywords.isEmpty() || !keywords.getFirst().equals("CREATE")) return false;
        return keywords.contains("TRIGGER");
    }

    private static void updateTriggerBlocks(String keyword, Deque<String> blocks) {
        if (keyword.equals("BEGIN") || keyword.equals("CASE")) {
            blocks.push(keyword);
        } else if (keyword.equals("END") && !blocks.isEmpty()) {
            blocks.pop();
        }
    }

    private static void addStatement(List<String> statements, StringBuilder current) {
        String sql = current.toString().trim();
        if (!sql.isEmpty() && !sql.equals(";")) statements.add(sql);
    }
}
