package learner;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Random;

import net.automatalib.automata.transout.MealyMachine;
import net.automatalib.commons.dotutil.DOT;
import net.automatalib.graphs.concepts.GraphViewable;
import net.automatalib.util.graphs.dot.GraphDOT;
import net.automatalib.words.Alphabet;
import net.automatalib.words.Word;
import net.automatalib.words.impl.SimpleAlphabet;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;

import de.learnlib.acex.analyzers.AcexAnalyzers;
import de.learnlib.algorithms.kv.mealy.KearnsVaziraniMealy;
import de.learnlib.algorithms.lstargeneric.ce.ObservationTableCEXHandlers;
import de.learnlib.algorithms.lstargeneric.closing.ClosingStrategies;
import de.learnlib.algorithms.lstargeneric.mealy.ExtensibleLStarMealy;
import de.learnlib.algorithms.ttt.mealy.TTTLearnerMealy;
import de.learnlib.api.EquivalenceOracle;
import de.learnlib.api.LearningAlgorithm;
import de.learnlib.api.MembershipOracle.MealyMembershipOracle;
import de.learnlib.api.SUL;
import de.learnlib.eqtests.basic.WMethodEQOracle;
import de.learnlib.eqtests.basic.WpMethodEQOracle;
import de.learnlib.eqtests.basic.mealy.RandomWalkEQOracle;
import de.learnlib.experiments.Experiment.MealyExperiment;
import de.learnlib.oracles.DefaultQuery;
import de.learnlib.oracles.ResetCounterSUL;
import de.learnlib.oracles.SULOracle;
import de.learnlib.oracles.SymbolCounterSUL;
import de.learnlib.statistics.Counter;

/**
 * General learning testing framework. The most important parameters are the input alphabet and the SUL (The
 * first two static attributes). Other settings can also be configured.
 * 
 * Based on the learner experiment setup of Joshua Moerman, https://gitlab.science.ru.nl/moerman/Learnlib-Experiments
 * 
 * @author Ramon Janssen
 */
public class Main {
	//*****************//
 	// SUL information //
	//*****************//
	// Defines the input alphabet, adapt for your socket (you can even use other types than string, if you 
	// change the generic-values, e.g. make your SUL of type SUL<Integer, Float> for int-input and float-output
	private static final Alphabet<String> inputAlphabet = new SimpleAlphabet<String>(ImmutableSet.of("a", "b", "c"));	
	// There are two SULs predefined, an example (see ExampleSul.java) and a socket SUL which connects to the SUL over socket
	private static final SULType sulType = SULType.Example;
	public enum SULType { Example, Socket }
	// For SULs over socket, the socket address/port can be set here
	private static final InetAddress socketIp = InetAddress.getLoopbackAddress();
	private static final int socketPort = 7890;
	private static final boolean printNewLineAfterEveryInput = true; // print newlines in the socket connection
	private static final String resetCmd = "RESET"; // the command to send over socket to reset sut
	
	//*******************//
 	// Learning settings //
	//*******************//
	// file for writing the resulting .dot-file and .pdf-file (extensions are added automatically)
	private static final String OUTPUT_FILENAME = "learnedModel";
	// the learning and testing algorithms. LStar is the basic algorithm, TTT performs much faster
	// but is a bit more inaccurate and produces more intermediate hypotheses, so test well)
	private static final LearningMethod learningAlgorithm = LearningMethod.LStar;
	public enum LearningMethod { LStar, RivestSchapire, TTT, KearnsVazirani }
	// Random walk is the simplest, but performs badly on large models: the chance of hitting a
	// erroneous long trace is very small
	private static final TestingMethod testMethod = TestingMethod.RandomWalk;
	public enum TestingMethod { RandomWalk, WMethod, WpMethod }
	// for random walk, the chance to do a reset after an input and the number of
	// inputs to test before accepting a hypothesis
	private static final double chanceOfResetting = 0.1; 
	private static final int numberOfSymbols = 100;
	// Simple experiments produce very little feedback, controlled produces feedback after
	// every hypotheses and are better suited to adjust by programming
	private static final boolean runControlledExperiment = true;
	// For controlled experiments only: store every hypotheses as a file. Useful for 'debugging'
	// if the learner does not terminate (hint: the TTT-algorithm produces many hypotheses).
	private static final boolean saveAllHypotheses = false;
	
	public static void main(String [] args) throws IOException {
		// Load the actual SUL-class, depending on which SUL-type is set at the top of this file
		// You can also program an own SUL-class if you extend SUL<String,String> (or SUL<S,T> in
		// general, with S and T the input and output types - you'll have to change some of the
		// code below)
		SUL<String,String> sul;
		switch (sulType) {
		case Example: 
			sul = new ExampleSUL();
			break;
		case Socket:
			sul = new SocketSUL(socketIp, socketPort, printNewLineAfterEveryInput, resetCmd);
			break;
		default:
			throw new RuntimeException("No SUL-type defined");
		}
		
		// Wrap the SUL in a detector for non-determinism
		sul = new NonDeterminismCheckingSUL<String,String>(sul);
		// Wrap the SUL in counters for symbols/resets, so that we can record some statistics
		SymbolCounterSUL<String, String> symbolCounterSul = new SymbolCounterSUL<>("symbol counter", sul);
		ResetCounterSUL<String, String> resetCounterSul = new ResetCounterSUL<>("reset counter", symbolCounterSul);
		Counter nrSymbols = symbolCounterSul.getStatisticalData(), nrResets = resetCounterSul.getStatisticalData();
		// we should use the sul only through those wrappers
		sul = resetCounterSul;
		// Most testing/learning-algorithms want a membership-oracle instead of a SUL directly
		MealyMembershipOracle<String,String> sulOracle = new SULOracle<>(sul);
		
		// Choosing an equivalence oracle
		EquivalenceOracle<MealyMachine<?, String, ?, String>, String, Word<String>> eqOracle = null;
		switch (testMethod){
			// simplest method, but doesn't perform well in practice, especially for large models
			case RandomWalk:
				eqOracle = new RandomWalkEQOracle<>(chanceOfResetting, numberOfSymbols, true, new Random(123456l), sul);
				break;
			// Other methods are somewhat smarter than random testing: state coverage, trying to distinguish states, etc.
			case WMethod:
				eqOracle = new WMethodEQOracle.MealyWMethodEQOracle<>(3, sulOracle);
				break;
			case WpMethod:
				eqOracle = new WpMethodEQOracle.MealyWpMethodEQOracle<>(3, sulOracle);
				break;
			default:
				throw new RuntimeException("No test oracle selected!");
		}

		// Choosing a learner
		LearningAlgorithm<MealyMachine<?, String, ?, String>, String, Word<String>> learner = null;
		switch (learningAlgorithm){
			case LStar:
				learner = new ExtensibleLStarMealy<>(inputAlphabet, sulOracle, Lists.<Word<String>>newArrayList(), ObservationTableCEXHandlers.CLASSIC_LSTAR, ClosingStrategies.CLOSE_SHORTEST);
				break;
			case RivestSchapire:
				learner = new ExtensibleLStarMealy<>(inputAlphabet, sulOracle, Lists.<Word<String>>newArrayList(), ObservationTableCEXHandlers.RIVEST_SCHAPIRE, ClosingStrategies.CLOSE_SHORTEST);
				break;
			case TTT:
				learner = new TTTLearnerMealy<>(inputAlphabet, sulOracle, AcexAnalyzers.LINEAR_FWD);
				break;
			case KearnsVazirani:
				learner = new KearnsVaziraniMealy<>(inputAlphabet, sulOracle, false, AcexAnalyzers.LINEAR_FWD);
				break;
			default:
				throw new RuntimeException("No learner selected");
		}
		
		// Running the actual experiments!
		if (runControlledExperiment) {
			runControlledExperiment(learner, eqOracle, nrSymbols, nrResets, inputAlphabet);
		} else {
			runSimpleExperiment(learner, eqOracle, inputAlphabet);
		}
	}
	
	/**
	 * Simple example of running a learning experiment
	 * @param learner Learning algorithm, wrapping the SUL
	 * @param eqOracle Testing algorithm, wrapping the SUL
	 * @param alphabet Input alphabet
	 * @throws IOException if the result cannot be written
	 */
	public static void runSimpleExperiment(
			LearningAlgorithm<MealyMachine<?, String, ?, String>, String, Word<String>> learner,
			EquivalenceOracle<MealyMachine<?, String, ?, String>, String, Word<String>> eqOracle,
			Alphabet<String> alphabet) throws IOException {
		MealyExperiment<String, String> experiment = new MealyExperiment<String, String>(learner, eqOracle, alphabet);
		experiment.run();
		System.out.println("Ran " + experiment.getRounds().getCount() + " rounds");
		produceOutput(OUTPUT_FILENAME, experiment.getFinalHypothesis(), alphabet, true);
	}
	
	/**
	 * More detailed example of running a learning experiment. Starts learning, and then loops testing,
	 * and if counterexamples are found, refining again. Also prints some statistics about the experiment
	 * @param learner learner Learning algorithm, wrapping the SUL
	 * @param eqOracle Testing algorithm, wrapping the SUL
	 * @param nrSymbols A counter for the number of symbols that have been sent to the SUL (for statistics)
	 * @param nrResets A counter for the number of resets that have been sent to the SUL (for statistics)
	 * @param alphabet Input alphabet
	 * @throws IOException
	 */
	public static void runControlledExperiment(
			LearningAlgorithm<MealyMachine<?, String, ?, String>, String, Word<String>> learner,
			EquivalenceOracle<MealyMachine<?, String, ?, String>, String, Word<String>> eqOracle,
			Counter nrSymbols, Counter nrResets,
			Alphabet<String> alphabet) throws IOException {
		
		// prepare some counters for printing statistics
		int stage = 0;
		long lastNrResetsValue = 0, lastNrSymbolsValue = 0;
		
		// start the actual learning
		learner.startLearning();
		
		while(true) {
			// store hypothesis as file
			if(saveAllHypotheses) {
				String outputFilename = "hyp." + stage + ".obf.dot";
				PrintWriter output = new PrintWriter(outputFilename);
				produceOutput(outputFilename, learner.getHypothesisModel(), alphabet, false);
				output.close();
			}

			// Print statistics
			System.out.println(stage + ": " + Calendar.getInstance().getTime());
			// Log number of queries/symbols
			System.out.println("Hypothesis size: " + learner.getHypothesisModel().size() + " states");
			long roundResets = nrResets.getCount() - lastNrResetsValue, roundSymbols = nrSymbols.getCount() - lastNrSymbolsValue;
			System.out.println("learning queries/symbols: " + nrResets.getCount() + "/" + nrSymbols.getCount()
					+ "(" + roundResets + "/" + roundSymbols + " this learning round)");
			lastNrResetsValue = nrResets.getCount();
			lastNrSymbolsValue = nrSymbols.getCount();
			
			// Search for CE
			DefaultQuery<String, Word<String>> ce = eqOracle.findCounterExample(learner.getHypothesisModel(), alphabet);
			
			// Log number of queries/symbols
			roundResets = nrResets.getCount() - lastNrResetsValue;
			roundSymbols = nrSymbols.getCount() - lastNrSymbolsValue;
			System.out.println("testing queries/symbols: " + nrResets.getCount() + "/" + nrSymbols.getCount()
					+ "(" + roundResets + "/" + roundSymbols + " this testing round)");
			lastNrResetsValue = nrResets.getCount();
			lastNrSymbolsValue = nrSymbols.getCount();
			
			if(ce == null) {
				// No counterexample found, stop learning
				System.out.println("\nFinished learning!");
				produceOutput(OUTPUT_FILENAME, learner.getHypothesisModel(), alphabet, true);
				break;
			} else {
				// Counterexample found, rinse and repeat
				System.out.println();
				stage++;
				learner.refineHypothesis(ce);
			}
		}
	}
	
	/**
	 * Produces a dot-file and a PDF (if graphviz is installed)
	 * @param fileName filename without extension - will be used for the .dot and .pdf
	 * @param model
	 * @param alphabet
	 * @param verboseError whether to print an error explaing that you need graphviz
	 * @throws FileNotFoundException
	 * @throws IOException
	 */
	public static void produceOutput(String fileName, MealyMachine<?,String,?,String> model, Alphabet<String> alphabet, boolean verboseError) throws FileNotFoundException, IOException {
		GraphDOT.write(model, alphabet, new PrintWriter(OUTPUT_FILENAME + ".dot"));
		try {
			DOT.runDOT(new File(OUTPUT_FILENAME + ".dot"), "pdf", new File(OUTPUT_FILENAME + ".pdf"));
		} catch (Exception e) {
			if (verboseError) {
				System.err.println("Warning: Install graphviz to convert dot-files to PDF");
				System.err.println(e.getMessage());
			}
		}
	}
}
