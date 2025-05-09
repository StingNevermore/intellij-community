/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.builtInWebServer.ssi;

import org.jetbrains.annotations.NonNls;

import java.text.ParseException;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Represents a parsed expression.
 *
 * @author Paul Speed
 */
final class ExpressionParseTree {
  /**
   * Contains the current set of completed nodes. This is a workspace for the
   * parser.
   */
  private final LinkedList<Node> nodeStack = new LinkedList<>();
  /**
   * Contains operator nodes that don't yet have values. This is a workspace
   * for the parser.
   */
  private final LinkedList<OppNode> oppStack = new LinkedList<>();
  /**
   * The root node after the expression has been parsed.
   */
  private Node root;
  /**
   * The SSIMediator to use when evaluating the expressions.
   */
  private final SsiProcessingState ssiProcessingState;

  /**
   * Creates a new parse tree for the specified expression.
   */
  ExpressionParseTree(String expr, SsiProcessingState ssiProcessingState)
    throws ParseException {
    this.ssiProcessingState = ssiProcessingState;
    parseExpression(expr);
  }


  /**
   * Evaluates the tree and returns true or false. The specified SSIMediator
   * is used to resolve variable references.
   */
  public boolean evaluateTree() {
    return root.evaluate();
  }


  /**
   * Pushes a new operator onto the opp stack, resolving existing opps as
   * needed.
   */
  private void pushOpp(OppNode node) {
    // If node is null then it's just a group marker
    if (node == null) {
      oppStack.add(0, null);
      return;
    }
    while (true) {
      if (oppStack.isEmpty()) break;
      OppNode top = oppStack.get(0);
      // If the top is a spacer then don't pop
      // anything
      if (top == null) break;
      // If the top node has a lower precedence then
      // let it stay
      if (top.getPrecedence() < node.getPrecedence()) break;
      // Remove the top node
      oppStack.remove(0);
      // Let it fill its branches
      top.popValues(nodeStack);
      // Stick it on the resolved node stack
      nodeStack.add(0, top);
    }
    // Add the new node to the opp stack
    oppStack.add(0, node);
  }


  /**
   * Resolves all pending opp nodes on the stack until the next group marker
   * is reached.
   */
  private void resolveGroup() {
    OppNode top;
    while ((top = oppStack.remove(0)) != null) {
      // Let it fill its branches
      top.popValues(nodeStack);
      // Stick it on the resolved node stack
      nodeStack.add(0, top);
    }
  }


  /**
   * Parses the specified expression into a tree of parse nodes.
   */
  private void parseExpression(String expr) throws ParseException {
    StringNode currStringNode = null;
    // We cheat a little and start an artificial
    // group right away. It makes finishing easier.
    pushOpp(null);
    ExpressionTokenizer et = new ExpressionTokenizer(expr);
    while (et.hasMoreTokens()) {
      int token = et.nextToken();
      if (token != ExpressionTokenizer.TOKEN_STRING) {
        currStringNode = null;
      }
      switch (token) {
        case ExpressionTokenizer.TOKEN_STRING -> {
          if (currStringNode == null) {
            currStringNode = new StringNode(et.getTokenValue());
            nodeStack.add(0, currStringNode);
          }
          else {
            // Add to the existing
            currStringNode.value.append(" ");
            currStringNode.value.append(et.getTokenValue());
          }
        }
        case ExpressionTokenizer.TOKEN_AND -> pushOpp(new AndNode());
        case ExpressionTokenizer.TOKEN_OR -> pushOpp(new OrNode());
        case ExpressionTokenizer.TOKEN_NOT -> pushOpp(new NotNode());
        case ExpressionTokenizer.TOKEN_EQ -> pushOpp(new EqualNode());
        case ExpressionTokenizer.TOKEN_NOT_EQ -> {
          pushOpp(new NotNode());
          // Sneak the regular node in. The NOT will
          // be resolved when the next opp comes along.
          oppStack.add(0, new EqualNode());
        }
        case ExpressionTokenizer.TOKEN_RBRACE ->
          // Closeout the current group
          resolveGroup();
        case ExpressionTokenizer.TOKEN_LBRACE ->
          // Push a group marker
          pushOpp(null);
        case ExpressionTokenizer.TOKEN_GE -> {
          pushOpp(new NotNode());
          // Similar strategy to NOT_EQ above, except this
          // is NOT less than
          oppStack.add(0, new LessThanNode());
        }
        case ExpressionTokenizer.TOKEN_LE -> {
          pushOpp(new NotNode());
          // Similar strategy to NOT_EQ above, except this
          // is NOT greater than
          oppStack.add(0, new GreaterThanNode());
        }
        case ExpressionTokenizer.TOKEN_GT -> pushOpp(new GreaterThanNode());
        case ExpressionTokenizer.TOKEN_LT -> pushOpp(new LessThanNode());
        case ExpressionTokenizer.TOKEN_END -> { }
      }
    }
    // Finish off the rest of the uopps
    resolveGroup();
    if (nodeStack.isEmpty()) {
      throw new ParseException("No nodes created.", et.getIndex());
    }
    if (nodeStack.size() > 1) {
      throw new ParseException("Extra nodes created.", et.getIndex());
    }
    if (!oppStack.isEmpty()) {
      throw new ParseException("Unused opp nodes exist.", et.getIndex());
    }
    root = nodeStack.get(0);
  }

  /**
   * A node in the expression parse tree.
   */
  private abstract static class Node {
    /**
     * Return true if the node evaluates to true.
     */
    public abstract boolean evaluate();
  }

  /**
   * A node the represents a String value
   */
  private class StringNode extends Node {
    StringBuilder value;
    String resolved = null;


    StringNode(String value) {
      this.value = new StringBuilder(value);
    }


    /**
     * Resolves any variable references and returns the value string.
     */
    public String getValue() {
      if (resolved == null) {
        resolved = ssiProcessingState.substituteVariables(value.toString());
      }
      return resolved;
    }


    /**
     * Returns true if the string is not empty.
     */
    @Override
    public boolean evaluate() {
      return !(getValue().isEmpty());
    }


    @Override
    public String toString() {
      return value.toString();
    }
  }

  private static final int PRECEDENCE_NOT = 5;
  private static final int PRECEDENCE_COMPARE = 4;
  private static final int PRECEDENCE_LOGICAL = 1;

  /**
   * A node implementation that represents an operation.
   */
  private abstract static class OppNode extends Node {
    /**
     * The left branch.
     */
    Node left;
    /**
     * The right branch.
     */
    Node right;


    /**
     * Returns a preference level suitable for comparison to other OppNode
     * preference levels.
     */
    public abstract int getPrecedence();


    /**
     * Lets the node pop its own branch nodes off the front of the
     * specified list. The default pulls two.
     */
    public void popValues(List<Node> values) {
      right = values.remove(0);
      left = values.remove(0);
    }
  }

  private static final class NotNode extends OppNode {
    @Override
    public boolean evaluate() {
      return !left.evaluate();
    }


    @Override
    public int getPrecedence() {
      return PRECEDENCE_NOT;
    }


    /**
     * Overridden to pop only one value.
     */
    @Override
    public void popValues(List<Node> values) {
      left = values.remove(0);
    }


    @Override
    public @NonNls String toString() {
      return left + " NOT";
    }
  }

  private static final class AndNode extends OppNode {
    @Override
    public boolean evaluate() {
      if (!left.evaluate()) // Short circuit
      {
        return false;
      }
      return right.evaluate();
    }


    @Override
    public int getPrecedence() {
      return PRECEDENCE_LOGICAL;
    }


    @Override
    public @NonNls String toString() {
      return left + " " + right + " AND";
    }
  }

  private static final class OrNode extends OppNode {
    @Override
    public boolean evaluate() {
      if (left.evaluate()) // Short circuit
      {
        return true;
      }
      return right.evaluate();
    }


    @Override
    public int getPrecedence() {
      return PRECEDENCE_LOGICAL;
    }


    @Override
    public @NonNls String toString() {
      return left + " " + right + " OR";
    }
  }

  private abstract static class CompareNode extends OppNode {
    protected int compareBranches() {
      String val1 = ((StringNode)left).getValue();
      String val2 = ((StringNode)right).getValue();

      int val2Len = val2.length();
      if (val2Len > 1 && val2.charAt(0) == '/' &&
          val2.charAt(val2Len - 1) == '/') {
        // Treat as a regular expression
        String expr = val2.substring(1, val2Len - 1);
        try {
          Pattern pattern = Pattern.compile(expr);
          // Regular expressions will only ever be used with EqualNode
          // so return zero for equal and non-zero for not equal
          if (pattern.matcher(val1).find()) {
            return 0;
          }
          else {
            return -1;
          }
        }
        catch (PatternSyntaxException e) {
          SsiProcessorKt.getLOG().warn("Invalid expression: " + expr, e);
          return 0;
        }
      }
      return val1.compareTo(val2);
    }
  }

  private static final class EqualNode extends CompareNode {
    @Override
    public boolean evaluate() {
      return (compareBranches() == 0);
    }


    @Override
    public int getPrecedence() {
      return PRECEDENCE_COMPARE;
    }


    @Override
    public @NonNls String toString() {
      return left + " " + right + " EQ";
    }
  }

  private static final class GreaterThanNode extends CompareNode {
    @Override
    public boolean evaluate() {
      return (compareBranches() > 0);
    }


    @Override
    public int getPrecedence() {
      return PRECEDENCE_COMPARE;
    }


    @Override
    public @NonNls String toString() {
      return left + " " + right + " GT";
    }
  }

  private static final class LessThanNode extends CompareNode {
    @Override
    public boolean evaluate() {
      return (compareBranches() < 0);
    }


    @Override
    public int getPrecedence() {
      return PRECEDENCE_COMPARE;
    }


    @Override
    public @NonNls String toString() {
      return left + " " + right + " LT";
    }
  }
}