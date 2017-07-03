package translator;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.util.List;

import prover.GdlRuleSet;
import util.GdlParser;
import util.grammar.GDLSyntaxException;
import util.grammar.Gdl;
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
		long startTime = System.nanoTime();
		boolean helpSwitch = false;
		boolean inputFileSwitch = false;
		boolean inputFileToken = false;
		boolean outputFileSwitch = false;
		boolean outputFileToken = false;
		boolean noGroundSwitch = false; // switch from ground to no-ground
		boolean orderedSwitch = false;
		boolean debugSwitch = false;
		boolean useDefineSwitch = false;
		boolean useProverSwitch = false;
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
			case "--no-ground":
				noGroundSwitch = true;
				break;
			case "--ordered":
				orderedSwitch = true;
				break;
			case "--use-define":
				useDefineSwitch = true;
				break;
			case "--use-prover":
				useProverSwitch = true;
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
			System.out.println("  -h --help      print this help file.");
			System.out.println("  -i --input     path to input file.");
			System.out.println("  -o --output    path to output file.");
			System.out.println("  --to-mck       output file is in mck format. (default)");
			System.out.println("  --to-lparse    output file is in lparse format.");
			System.out.println("  --to-dot       output domain graph in dot format.");
			System.out.println("  --to-dep-dot   output dependency graph in dot format.");
			System.out.println("  --pretty       formatted gdl.");
			System.out.println("  -g --no-ground don't use internal grounder.");
			System.out.println("  -d --debug     manually select outputs in debug mode.");
			System.out.println("  --use-define   construct mck output using define statements.");
			System.out.println("  --ordered      order the gdl rules (default true for --to-mck)");
			System.out.println("  --parse-tree   print parse tree for debug.");
			System.out.println("  --parse-types  print parse tree type for debug.");
		} else {
			// Scan and parse gdl
			List<String> tokens;
			GdlNode root = GdlNodeFactory.createGdl();
			GdlRuleSet ruleSet = null;
			File outputDir = new File(new File(inputFilePath).getName() + ".out");
			int dnfRuleSetSize = 0;
			int minDnfRuleSetSize = 0;
			long groundedRuleSetSize = 0;
			
			// Initialize prover
			try {
				System.out.print("Parsing ... ");
				if (inputFilePath.equals("")) {
					tokens = GdlParser.gdlTokenizer(new InputStreamReader(System.in));
				} else {
					tokens = GdlParser.tokenizeFile(inputFilePath);
				}
				root = GdlParser.expandParseTree(tokens);

				System.out.println("finished");
				printTimeDiff(startTime, System.nanoTime());
			} catch (URISyntaxException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}


			// Use internal grounder
			if (!noGroundSwitch) {
				System.out.print("Grounding ... ");
				//System.out.print("construct domain graph ... ");
				final DomainGraph domain = GdlParser.constructDomainGraph(root);
				System.out.print("constructed domain graph ... ");
				
				// Estimate size of grounded game
				for (GdlNode clause : root.getChildren()) {
					int resultingRules = 1;
					if (GdlParser.isVariableInTree(clause)) {
						for (GdlNode variable : GdlParser.variablesInTree(clause)) {
							resultingRules *= GdlParser.getVariableDomain(variable.getAtom(), clause, domain).size();
						}
					}
					groundedRuleSetSize += resultingRules;
				}
				//System.out.println("Ground from " + root.getChildren().size() + " -> " + totalRules);
				
				
				if (outputDotSwitch) {
					outputDir.mkdir();
					GdlParser.saveFile(domain.dotEncodedGraph(), outputDir.getName() + "/domain.dot");
				}
				
				try {
					if (useProverSwitch) {
						ruleSet = GdlParser.groundGdlToRuleSet(root, domain);
						dnfRuleSetSize = ruleSet.getRuleSet().size();
						ruleSet.cullVariables(true);
						minDnfRuleSetSize = ruleSet.getRuleSet().size();
					} else {
						root = GdlParser.groundGdl(root, domain);
					}
				} catch (GDLSyntaxException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

				System.out.println("gounding finished");
				printTimeDiff(startTime, System.nanoTime());
			}
			
			// Output dependency graph as a dot formatted file
			DependencyGraph graph = null;
			if (outputDepDotSwitch) {
				System.out.print("Generating dependency.dot ... ");
				if (useProverSwitch) {
					graph = GdlParser.constructDependencyGraph(GdlParser.parseString(ruleSet.toGdl()));
				} else {
					graph = GdlParser.constructDependencyGraph(root);
				}
				graph.computeStratum();
				outputDir.mkdir();
				GdlParser.saveFile(graph.dotEncodedGraph(), outputDir.getName() + "/dependency.dot");

				System.out.println("finished");
				printTimeDiff(startTime, System.nanoTime());
			}
			
			// Order rules by stratum
			// TODO: remove this section of code
			if (orderedSwitch) {
				System.out.print("Ordering rules ... ");
				if (graph == null) {
					root = GdlParser.parseString(GdlParser.orderGdlRules(root));
				} else {
					root = GdlParser.parseString(GdlParser.orderGdlRules(root, graph));
				}

				System.out.println("finished");
				printTimeDiff(startTime, System.nanoTime());
			}

			if (useProverSwitch) {
				System.out.print("Minimizing game ... ");
				try {
					if (ruleSet == null) {
						ruleSet = new GdlRuleSet((Gdl)root, debugSwitch);
						dnfRuleSetSize = ruleSet.getRuleSet().values().size();
						ruleSet.cullVariables(true);
					}
				} catch (GDLSyntaxException e) {
					useProverSwitch = false;
					e.printStackTrace();
				}
				System.out.println("finished");
				printTimeDiff(startTime, System.nanoTime());
			}
			
			// Print parse tree for debugging
			if (parseTreeSwitch) {
				outputDir.mkdir();
				GdlParser.saveFile(GdlParser.printParseTree(root), outputDir.getName() + "/parsetree");
			}

			// Print parse tree types for debugging
			if (parseTreeTypesSwitch) {
				outputDir.mkdir();
				GdlParser.saveFile(GdlParser.printParseTreeTypes(root), outputDir.getName() + "/treetypes");
			}

			// Output as another formatted gdl file
			if (prettyPrintSwitch) {
				outputDir.mkdir();
				if (useProverSwitch) {
					GdlParser.saveFile(ruleSet.toGdlOrdered(), outputDir.getName() + "/gameDesctiption.kif"); 
				} else {
					//GdlParser.saveFile(GdlParser.prettyPrint(root), outputDir.getName() + "/pretty.kif");
				}
			}

			// Output lparse for grounding (lparse cannot be read)
			if (outputLparseSwitch) {
				outputDir.mkdir();
				GdlParser.saveFile(GdlParser.toLparse(root), outputDir.getName() + "/unground.lparse");
			}

			if (outputFileSwitch || outputMckSwitch) {
				if (!orderedSwitch) {
					System.out.print("Ordering rules ... ");
					if (useProverSwitch) {
						root = GdlParser.parseString(ruleSet.toGdlOrdered());
					} else {
						root = GdlParser.parseString(GdlParser.orderGdlRules(root));
					}

					System.out.println("finished");
					printTimeDiff(startTime, System.nanoTime());
				}
				MckTranslator translator = null;
				if (useProverSwitch) {
					translator = new MckTranslator(ruleSet, useDefineSwitch, debugSwitch);
				} else {
					translator = new MckTranslator(ruleSet, useDefineSwitch, debugSwitch);
					//translator = new MckTranslator(root, useDefineSwitch, debugSwitch, ruleSet);
				}
				
				translator.setOutputHeader("-- Number of clauses after Grounding: " + groundedRuleSetSize + System.lineSeparator() 
						+ "-- Number of rules in RuleSet: " + dnfRuleSetSize + System.lineSeparator() 
						+ "-- Number of rules in RuleSet after Minimization: " + minDnfRuleSetSize + System.lineSeparator());
				
				System.out.print("Generating mck ... ");
				if (outputFileSwitch) {
					outputDir.mkdir();
					GdlParser.saveFile(translator.toMck(), outputFilePath);
				} else if (outputMckSwitch) {
					System.out.println(translator.toMck());
				}
				System.out.println("finished");
			}
			printTimeDiff(startTime, System.nanoTime());
		}
	}
	
	
	private static void printTimeDiff(long initialTime, long finalTime){
		long timeDiff = (finalTime - initialTime);
		System.out.print("Runtime: ");
		System.out.print((int)((timeDiff * ((1/60)*1e-9))) + "m ");
		System.out.print((int)((timeDiff * 1e-9) % 60) + ".");
		System.out.print((int)((timeDiff * 1e-6) % 1000) + "s ");
		System.out.println();
	}
}
