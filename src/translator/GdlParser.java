package translator;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import translator.MckTranslator.GdlType;
import translator.MckTranslator.ParseNode;

public class GdlParser {
	
	public final static char SEMI = ';';
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
				comment = false;
				sb = new StringBuilder();
				break;
			case SEMI:
				// comment
				comment = true;
				sb.append((char) character);
				break;
			default:
				// all other characters, usually part of atoms
				sb.append((char) character);
				break;
			}
		}

		return tokens;
	}

	/**
	 * Overloaded method which just asks for filePath as opposed to File object
	 */
	public static List<String> tokenizeFile(String filePath) throws IOException, URISyntaxException {
		return gdlTokenizer(new FileReader(new File(filePath)));
	}

	public static List<String> tokenizeGdl(String gdl) throws IOException {
		return gdlTokenizer(new StringReader(gdl));
	}

	/**
	 * Takes tokens and produces a parse tree returns ParseNode root of tree
	 */
	public static ParseNode expandParseTree(List<String> tokens) {
		ParseNode root = new ParseNode("", null, GdlType.ROOT);

		ParseNode parent = root;
		//boolean functionName = false;
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
			case MckTranslator.GDL_CLAUSE:
				ParseNode newNode = new ParseNode(token, parent, GdlType.CLAUSE);
				parent.getChildren().add(newNode);
				if (openBracket) {
					parent = newNode;
					openBracket = false;
				}
				break;
			default:
				newNode = new ParseNode(token, parent, GdlType.CONSTANT);
				if (parent.getType() == GdlType.CONSTANT) {
					parent.type = GdlType.FUNCTION;
				}

				if (newNode.getAtom().charAt(0) == '?') {
					newNode.type = GdlType.VARIABLE;
					scopedVariable = true;
					newNode.atom = newNode.getAtom() + "___" + scopeNumber;
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
}
