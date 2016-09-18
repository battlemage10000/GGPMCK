package translator;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import translator.grammar.GdlNode.GdlType;
import translator.grammar.GdlNode;
import translator.grammar.GdlNodeFactory;

public class GdlParser {

	public final static char SEMICOLON = ';';
	public final static char OPEN_P = '(';
	public final static char CLOSE_P = ')';
	public final static char SPACE = ' ';
	public final static char TAB = '\t';
	public final static char NEW_LINE = '\n';
	public final static char RETURN = '\r';

	/**
	 * Tokenises a file for GDL and also removes ';' comments
	 */
	public static List<String> gdlTokenizer(Reader file) throws IOException {
		List<String> tokens = new ArrayList<String>();

		StringBuilder sb = new StringBuilder();
		int character;
		boolean comment = false;
		while ((character = file.read()) != -1) {
			switch (character) {
			case OPEN_P:
			case CLOSE_P:
				// parenthesis
				if (sb.length() > 0 && !comment) {
					tokens.add(sb.toString());
				}
				if (!comment) {
					tokens.add(String.valueOf((char) character));
				}
				sb = new StringBuilder();
				break;
			case SPACE:
			case TAB:
				// whitespace
				if (sb.length() > 0 && !comment) {
					tokens.add(sb.toString());
				}
				sb = new StringBuilder();
				break;
			case NEW_LINE:
			case RETURN:
				// new line (ends comments)
				if (comment) {
					sb = new StringBuilder();
				}
				comment = false;
				break;
			case SEMICOLON:
				// start of comment
				comment = true;
				break;
			default:
				// all other characters, usually part of atoms
				sb.append((char) character);
				break;
			}
		}

		file.close();
		return tokens;
	}

	/**
	 * Overloaded method which doesn't require casting to Reader for common use
	 * cases
	 */
	public static List<String> tokenizeFile(String filePath) throws IOException, URISyntaxException {
		try (FileReader fr = new FileReader(new File(filePath))) {
			return gdlTokenizer(fr);
		}
	}

	/**
	 * Overloaded method which doesn't require casting to Reader for common use
	 * cases
	 */
	public static List<String> tokenizeString(String gdl) throws IOException {
		return gdlTokenizer(new StringReader(gdl));
	}

	/**
	 * Takes tokens and produces a parse tree returns ParseNode root of tree
	 */
	public static GdlNode expandParseTree(List<String> tokens) {
		GdlNode root = GdlNodeFactory.createGdl();

		GdlNode parent = root;
		boolean openBracket = false;
		boolean scopedVariable = false;
		int scopeNumber = 1;
		for (String token : tokens) {
			switch (token) {
			case "(":
				openBracket = true;
				break;
			case ")":
				parent = parent.getParent();
				if (scopedVariable == true && parent.getType() == GdlType.ROOT) {
					scopedVariable = false;
					scopeNumber++;
				}
				break;
			case GdlNode.GDL_CLAUSE:
				GdlNode newNode = GdlNodeFactory.createGdlRule(parent);
				parent.getChildren().add(newNode);
				if (openBracket) {
					parent = newNode;
					openBracket = false;
				}
				break;
			default:
				if (token.charAt(0) == '?') {
					scopedVariable = true;
					token = "?" + scopeNumber + "_" + token;
				}
				if (parent.getType() == GdlType.CLAUSE || parent.getType() == GdlType.ROOT) {
					newNode = GdlNodeFactory.createGdlFormula(token, parent);
				} else {
					newNode = GdlNodeFactory.createGdlTerm(token, parent);
				}
				parent.getChildren().add(newNode);
				if (openBracket) {
					parent = newNode;
					openBracket = false;
				}
				break;
			}
		}
		return root;
	}

	public static GdlNode parseFile(String filePath) throws IOException, URISyntaxException {
		return expandParseTree(tokenizeFile(filePath));
	}

	/**
	 * Overloaded method which doesn't require casting to Reader for common use
	 * cases
	 */
	public static GdlNode parseString(String gdl) throws IOException {
		return expandParseTree(tokenizeString(gdl));
	}
}
