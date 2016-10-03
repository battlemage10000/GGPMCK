package translator;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.util.List;

import util.GdlParser;
import util.grammar.GdlNode;
import util.grammar.GdlNodeFactory;
import util.graph.DependencyGraph;
import util.graph.DomainGraph;

public class Main {
	/**
	 * Can be used from the command line by moving to the build directory and
	 * using java translator.MckTranslator path/to/game.gdl or java -jar
	 * MckTranslator.jar path/to/game.gdl which will output to terminal
	 */
	public static void main(String[] args) {
		long startTime = System.currentTimeMillis();
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
			System.out.println("  -g --ground   use internal grounder(default)");
			System.out.println("  -d --debug    manually select outputs in debug mode");
			System.out.println("  --ordered     order the gdl rules (default true for --to-mck)");
			System.out.println("  --parse-tree  print parse tree for debug");
			System.out.println("  --parse-types print parse tree type for debug");
		} else {
			// Scan and parse gdl
			List<String> tokens;
			GdlNode root = GdlNodeFactory.createGdl();
			try {
				if (inputFilePath.equals("")) {
					tokens = GdlParser.gdlTokenizer(new InputStreamReader(System.in));
				} else {
					tokens = GdlParser.tokenizeFile(inputFilePath);
				}
				root = GdlParser.expandParseTree(tokens);
			} catch (URISyntaxException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
			
			File outputDir = new File("output");
			outputDir.mkdir();
			
			// Use internal grounder
			if (groundSwitch) {
				DomainGraph domain = GdlParser.constructDomainGraph(root);
				if (outputDotSwitch) {
					GdlParser.saveFile(domain.dotEncodedGraph(), "output/domain.dot");
				}
				root = GdlParser.groundGdl(root, domain);
			}

			// Order rules by stratum
			if (orderedSwitch || !outputLparseSwitch) {
				root = GdlParser.parseString(MckTranslator.orderGdlRules(root));
			}
			
			// Output dependency graph as a dot formatted file
			if (outputDepDotSwitch) {
				DependencyGraph graph = GdlParser.constructDependencyGraph(root);
				GdlParser.saveFile(graph.dotEncodedGraph(), "output/dependency.dot");
			}

			// Print parse tree for debugging
			if (parseTreeSwitch) {
				GdlParser.saveFile(GdlParser.printParseTree(root), "output/parsetree");
			}

			// Print parse tree types for debugging
			if (parseTreeTypesSwitch) {
				GdlParser.saveFile(GdlParser.printParseTreeTypes(root), "output/treetypes");
			}

			// Output as another formatted gdl file
			if (prettyPrintSwitch) {
				GdlParser.saveFile(GdlParser.prettyPrint(root), "output/pretty.kif");
			}
			
			// Output lparse for grounding (lparse cannot be read)
			if (outputLparseSwitch) {
				GdlParser.saveFile(GdlParser.toLparse(root), "output/unground.lparse");
			}
			

			if (outputFileSwitch) {
				GdlParser.saveFile(MckTranslator.toMck(root), outputFilePath);
			} else if (!debugSwitch || outputMckSwitch) {
				System.out.println(MckTranslator.toMck(root));
			}
			int totalTime = (int) (System.currentTimeMillis() - startTime);
			System.out.println("Runtime: " + (totalTime / 60000) + " minutes, " + (totalTime % 60000 / 1000) + " seconds");
		}
	}
}
