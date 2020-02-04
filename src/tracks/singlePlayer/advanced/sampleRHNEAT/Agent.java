package tracks.singlePlayer.advanced.sampleRHNEAT;

import core.game.Observation;
import core.game.StateObservation;
import core.player.AbstractPlayer;
import ontology.Types;
import tools.ElapsedCpuTimer;
import tools.Vector2d;
import tracks.singlePlayer.advanced.sampleRHNEAT.neat.Client;
import tracks.singlePlayer.advanced.sampleRHNEAT.neat.Neat;
import tracks.singlePlayer.advanced.sampleRHNEAT.visual.Frame;
import tracks.singlePlayer.tools.Heuristics.StateHeuristic;
import tracks.singlePlayer.tools.Heuristics.WinScoreHeuristic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Random;


public class Agent extends AbstractPlayer{

    // Parameters
    private int POPULATION_SIZE = 10;
    private StateHeuristic heuristic;
    // Constants
    private final long BREAK_MS = 10;
    // Class vars
    private int N_ACTIONS;
    private HashMap<Integer, Types.ACTIONS> action_mapping;
    private int INPUT_SIZE;
    // Budgets
    private ElapsedCpuTimer timer;
    private double acumTimeTakenEval = 0,avgTimeTakenEval = 0, avgTimeTaken = 0, acumTimeTaken = 0;
    private int numEvals = 0, numIters = 0;
    private boolean keepIterating = true;
    private long remaining;

    // Legacy variables
    private int SIMULATION_DEPTH = 10;
    private int CROSSOVER_TYPE = UNIFORM_CROSS;
    private boolean REEVALUATE = false;
    private int MUTATION = 1;
    private int TOURNAMENT_SIZE = 2;
    private int ELITISM = 1;
    public static final double epsilon = 1e-6;
    static final int POINT1_CROSS = 0;
    static final int UNIFORM_CROSS = 1;
    private Individual[] population, nextPop;
    private int NUM_INDIVIDUALS;
    private Random randomGenerator;

    public Agent(StateObservation stateObs, ElapsedCpuTimer elapsedTimer) {
        randomGenerator = new Random();
        heuristic = new WinScoreHeuristic(stateObs);
        this.timer = elapsedTimer;
    }

    @Override
    public Types.ACTIONS act(StateObservation stateObs, ElapsedCpuTimer elapsedTimer) {
        this.timer = elapsedTimer;
        avgTimeTaken = 0;
        acumTimeTaken = 0;
        numEvals = 0;
        acumTimeTakenEval = 0;
        numIters = 0;
        remaining = timer.remainingTimeMillis();
        NUM_INDIVIDUALS = 0;
        keepIterating = true;

        // NEAT
        Neat neat = init_neat(stateObs);

        // RUN EVOLUTION
        remaining = timer.remainingTimeMillis();
        while (remaining > avgTimeTaken && remaining > BREAK_MS && keepIterating) {
            evolve(stateObs, neat);
            System.out.println(neat.getBestClient().getScore());
            remaining = timer.remainingTimeMillis();
        }

        // RETURN ACTION, we have to return the best clients very first output
        double[] bestAction = neat.getBestClient().calculate(networkInput());
        return action_mapping.get((int)bestAction[0]);
    }

    private Neat init_neat(StateObservation stateObs) {

        double remaining = timer.remainingTimeMillis();

        N_ACTIONS = stateObs.getAvailableActions().size() + 1;
        action_mapping = new HashMap<>();
        int k = 0;
        for (Types.ACTIONS action : stateObs.getAvailableActions()) {
            action_mapping.put(k, action);
            k++;
        }
        action_mapping.put(k, Types.ACTIONS.ACTION_NIL);

        //PROB: This would vary depending on the game not very General Video Game AI, how to know all inputs before?
        INPUT_SIZE = 3; // Might vary depending on the game (asteroids)

        Neat neat = new Neat(INPUT_SIZE, N_ACTIONS, POPULATION_SIZE); //input nodes, output nodes, number of clients
        new Frame(neat.empty_genome());

        //TO DO: translate these into the NEAT input
        //networkInput(stateObs);
        ArrayList<Observation>[] npcPos = stateObs.getNPCPositions();
        ArrayList<Observation>[][] obsGrid = stateObs.getObservationGrid();
        Vector2d orient = stateObs.getAvatarOrientation();

        for (int i = 0; i < neat.getMax_clients(); i++) {
            if (i == 0 || remaining > avgTimeTakenEval && remaining > BREAK_MS) {
                rateClients(stateObs, neat.getClient(i), heuristic);
                remaining = timer.remainingTimeMillis();
            }
            else {
                break;
            }
        }

       return neat;
    }

    //methods to do
    private double[] networkInput() {
        return null;
    }
    private void evolve(StateObservation stateObs, Neat neat) {
    }
    private void rateClients(StateObservation state, Client client, StateHeuristic heuristic) {
        ElapsedCpuTimer elapsedTimerIterationEval = new ElapsedCpuTimer();
        StateObservation st = state.copy();

        client.setScore(heuristic.evaluateState(st));
        numEvals++;
        acumTimeTakenEval += (elapsedTimerIterationEval.elapsedMillis());
        avgTimeTakenEval = acumTimeTakenEval / numEvals;
        remaining = timer.remainingTimeMillis();
    }

    // Legacy code
    private double evaluate(Individual individual, StateHeuristic heuristic, StateObservation state) {

        ElapsedCpuTimer elapsedTimerIterationEval = new ElapsedCpuTimer();

        StateObservation st = state.copy();
        int i;
        double acum = 0, avg;
        for (i = 0; i < SIMULATION_DEPTH; i++) {
            if (! st.isGameOver()) {
                ElapsedCpuTimer elapsedTimerIteration = new ElapsedCpuTimer();
                st.advance(action_mapping.get(individual.actions[i]));

                acum += elapsedTimerIteration.elapsedMillis();
                avg = acum / (i+1);
                remaining = timer.remainingTimeMillis();
                if (remaining < 2*avg || remaining < BREAK_MS) break;
            } else {
                break;
            }
        }

        individual.value = heuristic.evaluateState(st);

        numEvals++;
        acumTimeTakenEval += (elapsedTimerIterationEval.elapsedMillis());
        avgTimeTakenEval = acumTimeTakenEval / numEvals;
        remaining = timer.remainingTimeMillis();

        return individual.value;
    }
    private void runIteration(StateObservation stateObs) {
        ElapsedCpuTimer elapsedTimerIteration = new ElapsedCpuTimer();

        if (REEVALUATE) {
            for (int i = 0; i < ELITISM; i++) {
                if (remaining > 2*avgTimeTakenEval && remaining > BREAK_MS) { // if enough time to evaluate one more individual
                    evaluate(population[i], heuristic, stateObs);
                } else {keepIterating = false;}
            }
        }

        if (NUM_INDIVIDUALS > 1) {
            for (int i = ELITISM; i < NUM_INDIVIDUALS; i++) {
                if (remaining > 2*avgTimeTakenEval && remaining > BREAK_MS) { // if enough time to evaluate one more individual
                    Individual newind;

                    newind = crossover();
                    newind = newind.mutate(MUTATION);

                    // evaluate new individual, insert into population
                    add_individual(newind, nextPop, i, stateObs);

                    remaining = timer.remainingTimeMillis();
                } else {
                    keepIterating = false;
                    break;
                }
            }

            Arrays.sort(nextPop, (o1, o2) -> {
                if (o1 == null && o2 == null) {
                    return 0;
                }
                if (o1 == null) {
                    return 1;
                }
                if (o2 == null) {
                    return -1;
                }
                return o1.compareTo(o2);
            });

        } else if (NUM_INDIVIDUALS == 1){
            Individual newind = new Individual(SIMULATION_DEPTH, N_ACTIONS, randomGenerator).mutate(MUTATION);
            evaluate(newind, heuristic, stateObs);
            if (newind.value > population[0].value)
                nextPop[0] = newind;
        }

        population = nextPop.clone();

        numIters++;
        acumTimeTaken += (elapsedTimerIteration.elapsedMillis());
        avgTimeTaken = acumTimeTaken / numIters;
    }
    private Individual crossover() {
        Individual newind = null;
        if (NUM_INDIVIDUALS > 1) {
            newind = new Individual(SIMULATION_DEPTH, N_ACTIONS, randomGenerator);
            Individual[] tournament = new Individual[TOURNAMENT_SIZE];
            ArrayList<Individual> list = new ArrayList<>(Arrays.asList(population));

            //Select a number of random distinct individuals for tournament and sort them based on value
            for (int i = 0; i < TOURNAMENT_SIZE; i++) {
                int index = randomGenerator.nextInt(list.size());
                tournament[i] = list.get(index);
                list.remove(index);
            }
            Arrays.sort(tournament);

            //get best individuals in tournament as parents
            if (TOURNAMENT_SIZE >= 2) {
                newind.crossover(tournament[0], tournament[1], CROSSOVER_TYPE);
            } else {
                System.out.println("WARNING: Number of parents must be LESS than tournament size.");
            }
        }
        return newind;
    }
    private void add_individual(Individual newind, Individual[] pop, int idx, StateObservation stateObs) {
        evaluate(newind, heuristic, stateObs);
        pop[idx] = newind.copy();
    }
    private Types.ACTIONS get_best_action(Individual[] pop) {
        int bestAction = pop[0].actions[0];
        return action_mapping.get(bestAction);
    }
}

