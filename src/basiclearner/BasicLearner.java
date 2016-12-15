package basiclearner;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Calendar;
import java.util.Collection;
import java.util.Random;

import net.automatalib.automata.transout.MealyMachine;
import net.automatalib.commons.dotutil.DOT;
import net.automatalib.util.graphs.dot.GraphDOT;
import net.automatalib.words.Alphabet;
import net.automatalib.words.Word;
import net.automatalib.words.impl.SimpleAlphabet;

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
 * General learning testing framework. All basic settings are at the top of this file and can be configured
 * by hard-coding or by simply changing them from your own code.
 * 
 * Based on the learner experiment setup of Joshua Moerman, https://gitlab.science.ru.nl/moerman/Learnlib-Experiments
 * 
 * @author Ramon Janssen
 */
public class BasicLearner {
	//***********************************************************************************//
 	// Learning settings (hardcoded, simply set to a different value to change learning) //
	//***********************************************************************************//
	/**
	 * name to give to the resulting .dot-file and .pdf-file (extensions are added automatically)
	 */
	public static String
			FINAL_MODEL_FILENAME = "learnedModel",
			INTERMEDIATE_HYPOTHESIS_FILENAME = "hypothesis";
	/**
	 * For controlled experiments only: store every hypotheses as a file. Useful for 'debugging'
	 * if the learner does not terminate (hint: the TTT-algorithm produces many hypotheses).
	 */
	public static boolean saveAllHypotheses = true;
	/**
	 * For random walk, the chance to reset after every input
	 */
	public static double randomWalk_chanceOfResetting = 0.1;
	/**
	 * For random walk, the number of symbols that is tested in total (divided over multiple traces).
	 */
	public static int randomWalk_numberOfSymbols = 300;
	/**
	 * MaxDepth-parameter for W-method and Wp-method. Typically not larger than 3. Decrease for quicker runs.
	 */
	public static int w_wp_methods_maxDepth = 2;

	//*****************************************//
	// Predefined learning and testing methods //
	//*****************************************//
	/**
	 * The learning algorithms. LStar is the basic algorithm, TTT performs much faster
	 * but is a bit more inaccurate and produces more intermediate hypotheses, so test well)
	 */
	public enum LearningMethod { LStar, RivestSchapire, TTT, KearnsVazirani }
	/**
	 * The testing algorithms. Random walk is the simplest, but performs badly on large models:
	 * the chance of hitting a erroneous long trace is very small. WMethod and WpMethod are
	 * smarter. UserQueries asks the user for which inputs to try as counter-example: have a
	 * look at the hypothesis, and try to think of one
	 */
	public enum TestingMethod { RandomWalk, WMethod, WpMethod, UserQueries }

	public static LearningAlgorithm<MealyMachine<?, String, ?, String>, String, Word<String>> loadLearner(
			LearningMethod learningMethod, MealyMembershipOracle<String,String> sulOracle, Alphabet<String> alphabet) {
		switch (learningMethod){
			case LStar:
				return new ExtensibleLStarMealy<String, String>(alphabet, sulOracle, Lists.<Word<String>>newArrayList(), ObservationTableCEXHandlers.CLASSIC_LSTAR, ClosingStrategies.CLOSE_SHORTEST);
			case RivestSchapire:
				return new ExtensibleLStarMealy<String, String>(alphabet, sulOracle, Lists.<Word<String>>newArrayList(), ObservationTableCEXHandlers.RIVEST_SCHAPIRE, ClosingStrategies.CLOSE_SHORTEST);
			case TTT:
				return new TTTLearnerMealy<String, String>(alphabet, sulOracle, AcexAnalyzers.LINEAR_FWD);
			case KearnsVazirani:
				return new KearnsVaziraniMealy<String, String>(alphabet, sulOracle, false, AcexAnalyzers.LINEAR_FWD);
			default:
				throw new RuntimeException("No learner selected");
		}
	}

	public static EquivalenceOracle<MealyMachine<?, String, ?, String>, String, Word<String>> loadTester(
			TestingMethod testMethod, SUL<String,String> sul, MealyMembershipOracle<String,String> sulOracle) {
		switch (testMethod){
			// simplest method, but doesn't perform well in practice, especially for large models
			case RandomWalk:
				return new RandomWalkEQOracle<>(randomWalk_chanceOfResetting, randomWalk_numberOfSymbols, true, new Random(123456l), sul);
			// Other methods are somewhat smarter than random testing: state coverage, trying to distinguish states, etc.
			case WMethod:
				return new WMethodEQOracle.MealyWMethodEQOracle<>(w_wp_methods_maxDepth, sulOracle);
			case WpMethod:
				return new WpMethodEQOracle.MealyWpMethodEQOracle<>(w_wp_methods_maxDepth, sulOracle);
			case UserQueries:
				return new UserEQOracle(sul);
			default:
				throw new RuntimeException("No test oracle selected!");
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
		MealyExperiment<String, String> experiment
				= new MealyExperiment<String, String>(learner, eqOracle, alphabet);
		experiment.run();
		System.out.println("Ran " + experiment.getRounds().getCount() + " rounds");
		produceOutput(FINAL_MODEL_FILENAME, experiment.getFinalHypothesis(), alphabet, true);
	}

	/**
	 * Simple example of running a learning experiment
	 * @param sul Direct access to SUL
	 * @param learningMethod One of the default learning methods from this class
	 * @param testingMethod One of the default testing methods from this class
	 * @param alphabet Input alphabet
	 * @throws IOException if the result cannot be written
	 */
	public static void runSimpleExperiment (
			SUL<String,String> sul,
			LearningMethod learningMethod,
			TestingMethod testingMethod,
			Collection<String> alphabet
			) throws IOException {
		Alphabet<String> learlibAlphabet = new SimpleAlphabet<String>(alphabet);
		LearningSetup learningSetup = new LearningSetup(sul, learningMethod, testingMethod, learlibAlphabet);
		runSimpleExperiment(learningSetup.learner, learningSetup.eqOracle, learlibAlphabet);
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
		try {
			// prepare some counters for printing statistics
			int stage = 0;
			long lastNrResetsValue = 0, lastNrSymbolsValue = 0;
			
			// start the actual learning
			learner.startLearning();
			
			while(true) {
				// store hypothesis as file
				if(saveAllHypotheses) {
					String outputFilename = INTERMEDIATE_HYPOTHESIS_FILENAME + stage;
					produceOutput(outputFilename, learner.getHypothesisModel(), alphabet, false);
					System.out.println("model size " + learner.getHypothesisModel().getStates().size());
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
					produceOutput(FINAL_MODEL_FILENAME, learner.getHypothesisModel(), alphabet, true);
					break;
				} else {
					// Counterexample found, rinse and repeat
					System.out.println();
					stage++;
					learner.refineHypothesis(ce);
				}
			}
		} catch (Exception e) {
			String errorHypName = "hyp.before.crash.dot";
			produceOutput(errorHypName, learner.getHypothesisModel(), alphabet, true);
			throw e;
		}
	}

	/**
	 * More detailed example of running a learning experiment. Starts learning, and then loops testing,
	 * and if counterexamples are found, refining again. Also prints some statistics about the experiment
	 * @param sul Direct access to SUL
	 * @param learningMethod One of the default learning methods from this class
	 * @param testingMethod One of the default testing methods from this class
	 * @param alphabet Input alphabet
	 * @param alphabet Input alphabet
	 * @throws IOException
	 */
	public static void runControlledExperiment(
			SUL<String,String> sul,
			LearningMethod learningMethod,
			TestingMethod testingMethod,
			Collection<String> alphabet
		) throws IOException {
		Alphabet<String> learnlibAlphabet = new SimpleAlphabet<String>(alphabet);
		LearningSetup learningSetup = new LearningSetup(sul, learningMethod, testingMethod, learnlibAlphabet);
		runControlledExperiment(learningSetup.learner, learningSetup.eqOracle, learningSetup.nrSymbols, learningSetup.nrResets, learnlibAlphabet);
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
		PrintWriter dotWriter = new PrintWriter(fileName + ".dot");
		GraphDOT.write(model, alphabet, dotWriter);
		try {
			DOT.runDOT(new File(fileName + ".dot"), "pdf", new File(fileName + ".pdf"));
		} catch (Exception e) {
			if (verboseError) {
				System.err.println("Warning: Install graphviz to convert dot-files to PDF");
				System.err.println(e.getMessage());
			}
		}
		dotWriter.close();
	}

	/**
	 * Helper class to configure a learning and equivalence oracle. Tell it which learning and testing method you
	 * want, and it produces the corresponding oracles (and counters for statistics) as attributes.
	 */
	public static class LearningSetup {
		public final EquivalenceOracle<MealyMachine<?, String, ?, String>, String, Word<String>> eqOracle;
		public final LearningAlgorithm<MealyMachine<?, String, ?, String>, String, Word<String>> learner;
		public final Counter nrSymbols, nrResets;

		public LearningSetup(SUL<String,String> sul, LearningMethod learningMethod, TestingMethod testingMethod, Alphabet<String> alphabet) {
			// Wrap the SUL in a detector for non-determinism
			SUL<String,String> nonDetSul = new NonDeterminismCheckingSUL<String,String>(sul);
			// Wrap the SUL in counters for symbols/resets, so that we can record some statistics
			SymbolCounterSUL<String, String> symbolCounterSul = new SymbolCounterSUL<>("symbol counter", nonDetSul);
			ResetCounterSUL<String, String> resetCounterSul = new ResetCounterSUL<>("reset counter", symbolCounterSul);
			nrSymbols = symbolCounterSul.getStatisticalData();
			nrResets = resetCounterSul.getStatisticalData();
			// we should use the sul only through those wrappers
			sul = resetCounterSul;
			// Most testing/learning-algorithms want a membership-oracle instead of a SUL directly
			MealyMembershipOracle<String,String> sulOracle = new SULOracle<>(sul);

			// Choosing an equivalence oracle
			eqOracle = loadTester(testingMethod, sul, sulOracle);

			// Choosing a learner
			learner = loadLearner(learningMethod, sulOracle, alphabet);
		}
	}
}
