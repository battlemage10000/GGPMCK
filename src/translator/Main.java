package translator;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.util.List;

import translator.grammar.GdlNode;
import translator.graph.DependencyGraph;
import translator.graph.DomainGraph;

public class Main extends MckTranslator {
	/**
	 * Can be used from the command line by moving to the build directory and
	 * using java translator.MckTranslator path/to/game.gdl or java -jar
	 * MckTranslator.jar path/to/game.gdl which will save output to
	 * path/to/game.gdl.mck
	 */
	public static void main(String[] args) {
		boolean helpSwitch = false;
		boolean inputFileSwitch = false;
		boolean inputFileToken = false;
		boolean outputFileSwitch = false;
		boolean outputFileToken = false;
		boolean groundSwitch = false;
		boolean orderedSwitch = false;
		boolean debugSwitch = false;
		boolean outputMckSwitch = false;
		boolean outputLparseSwitch = false;
		boolean outputDotSwitch = false;
		boolean outputDepDotSwitch = false;
		boolean prettyPrintSwitch = false;
		boolean parseTreeSwitch = false;
		boolean parseTreeTypesSwitch = false;

		String inputFilePath = "";
		String outputFilePath = "";

		for (String arg : args) {
			switch (arg) {
			case "-h":
			case "--help":
				helpSwitch = true;
				break;
			case "-o":
			case "--output":
				outputFileSwitch = true;
				outputFileToken = true;
				break;
			case "-i":
			case "--input":
				inputFileSwitch = true;
				inputFileToken = true;
				break;
			case "-g":
			case "--ground":
				groundSwitch = true;
				break;
			case "--ordered":
				orderedSwitch = true;
				break;
			case "-d":
			case "--debug":
				debugSwitch = true;
				break;
			case "--to-mck":
				outputMckSwitch = true;
				break;
			case "--to-lparse":
				outputLparseSwitch = true;
				break;
			case "--to-dot":
				outputDotSwitch = true;
				break;
			case "--to-dep-dot":
				outputDepDotSwitch = true;
				break;
			case "--pretty":
				prettyPrintSwitch = true;
				break;
			case "--parse-tree":
				parseTreeSwitch = true;
				break;
			case "--parse-types":
				parseTreeTypesSwitch = true;
				break;
			default:
				if (outputFileToken) {
					outputFilePath = arg;
					outputFileToken = false;
				} else if (inputFileToken) {
					inputFilePath = arg;
					inputFileToken = false;
				} else if (!inputFileSwitch) {
					inputFilePath = arg;
				}
			}
		}

		if (helpSwitch) {
			System.out.println("usage: java -jar MckTranslator.jar [options] [gdlFileInput]");
			System.out.println("Options:");
			System.out.println("  -h --help     print this help file");
			System.out.println("  -i --input    path to input file (default: stdin)");
			System.out.println("  -o --output   path to output file (default: stdout)");
			System.out.println("  --to-mck      output file is in mck format (default)");
			System.out.println("  --to-lparse   output file is in lparse format");
			System.out.println("  --to-dot      output domain graph in dot format. Use with --ground");
			System.out.println("  --to-dep-dot  output dependency graph in dot format");
			System.out.println("  --pretty      formatted gdl. Use with --ground");
			System.out.println("  -g --ground   use internal grounder");
			System.out.println("  -d --debug    manually select outputs in debug mode");
			System.out.println("  --ordered     order the gdl rules (default true for --to-mck)");
			System.out.println("  --parse-tree  print parse tree for debug");
			System.out.println("  --parse-types print parse tree type for debug");
		} else {
			try {
				// Use either
				List<String> tokens;
				if (inputFilePath.equals("")) {
					tokens = GdlParser.gdlTokenizer(new InputStreamReader(System.in));
				} else {
					tokens = GdlParser.tokenizeFile(inputFilePath);
				}
				GdlNode root = GdlParser.expandParseTree(tokens);

				// Use internal grounder
				if (groundSwitch) {
					DomainGraph domain = constructDomainGraph(root);
					if (outputDotSwitch) {
						System.out.println(domain.dotEncodedGraph());
					}
					root = groundGdl(root, domain);
				}

				// Order rules by stratum
				if (orderedSwitch || !outputLparseSwitch) {
					root = GdlParser.parseString(orderGdlRules(root));
				}
				
				if (outputDepDotSwitch) {
					DependencyGraph graph = constructDependencyGraph(root);
					System.out.println(graph.dotEncodedGraph());
				}

				// Print parse tree for debugging
				if (parseTreeSwitch) {
					printParseTree(root);
				}

				// Print parse tree types for debugging
				if (parseTreeTypesSwitch) {
					printParseTreeTypes(root);
				}

				if (prettyPrintSwitch) {
					prettyPrint(root);
				}

				String translation;
				if (outputLparseSwitch) {
					translation = toLparse(root);
				} else {
					//root = GdlParser.parseString(orderGdlRules(root));
					translation = toMck(root);
				}

				if (outputFileSwitch) {
					saveFile(translation, outputFilePath);
				} else if (!debugSwitch || outputLparseSwitch || outputMckSwitch) {
					System.out.println(translation);
				}
			} catch (URISyntaxException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
}
