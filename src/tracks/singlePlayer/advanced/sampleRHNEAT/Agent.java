package tracks.singlePlayer.advanced.sampleRHNEAT;

import core.game.Observation;
import core.game.StateObservation;
import core.player.AbstractPlayer;
import ontology.Types;
import tools.ElapsedCpuTimer;
import tools.Vector2d;
import tracks.singlePlayer.advanced.sampleRHNEAT.neat.Client;
import tracks.singlePlayer.advanced.sampleRHNEAT.neat.Neat;
import tracks.singlePlayer.tools.Heuristics.StateHeuristic;
import tracks.singlePlayer.tools.Heuristics.WinScoreHeuristic;

import java.util.ArrayList;
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
        remaining = timer.remainingTimeMillis();
        numEvals = 0;
        acumTimeTakenEval = 0;
        numIters = 0;
        keepIterating = true;

        // NEAT
        Neat neat = init_neat(stateObs);

        // RUN EVOLUTION
        remaining = timer.remainingTimeMillis();
        while (remaining > avgTimeTaken && remaining > BREAK_MS && keepIterating) {
            evolve(stateObs, neat);
          //  System.out.println(neat.getBestClient().getScore());
            remaining = timer.remainingTimeMillis();
        }

        // RETURN ACTION, we have to return the best clients very first output

        double[] bestAction = neat.getBestClient().calculate(extractNetworkInput(stateObs));
        /*
        System.out.println("#################");
        for(int i=0; i<bestAction.length; i++) {
            System.out.println(bestAction[i]);
        }
        System.out.println("#################");
         */
        int index = 0;
        for(int j = 1; j < bestAction.length; j++){
            if(bestAction[j] > bestAction[index]){
                index = j;
            }
        }
        //return action_mapping.get((int)bestAction[0]);

        return action_mapping.get(index);
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
        INPUT_SIZE = extractNetworkInput(stateObs).length; // Might vary depending on the game (asteroids)

        Neat neat = new Neat(INPUT_SIZE, N_ACTIONS, POPULATION_SIZE); //input nodes, output nodes, number of clients
        //new Frame(neat.empty_genome());

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

    //methods to do tailored to asteroids only for now
    private double[] extractNetworkInput(StateObservation stateObs) {

        int outputSize = 2;
        double dotProduct = 0;

        Vector2d position = stateObs.getAvatarPosition();
        Vector2d orient = stateObs.getAvatarOrientation();
        ArrayList<Observation>[] imovPos = stateObs.getImmovablePositions(position);
        //Dimension dim = stateObs.getWorldDimension();
        //.out.println();

        //ArrayList<Observation>[] npcPos = stateObs.getNPCPositions(); //Other games
        //ArrayList<Observation>[] enemyPos = stateObs.getMovablePositions(position); //Other Games

        if(!(imovPos == null)) { //angle between player and asteroid
            Observation ob = imovPos[0].get(0);
            Vector2d target = ob.position;
            position.normalise();
            target.normalise();
            //dotProduct = Math.atan2(target.y, target.x) - Math.atan2(position.y, position.x);
            dotProduct = target.dot(orient);
            //System.out.println(dotProduct);
            outputSize = 3;
        }

        double[] out = new double[outputSize];
        out[0] = stateObs.getGameScore(); //Game score
        out[1] = stateObs.getAvatarSpeed(); //Avatar velocity
        if (outputSize == 3) out[2] = dotProduct; //Angle between player and enemy

        return out;
    }
    private void evolve(StateObservation stateObs, Neat neat) {
        for(int i = 0; i < neat.getMax_clients(); i++){
            rateClients(stateObs, neat.getClient(i), heuristic);
        }
        neat.evolve();
    }
    private double rateClients(StateObservation state, Client client, StateHeuristic heuristic) {
        ElapsedCpuTimer elapsedTimerIterationEval = new ElapsedCpuTimer();
        StateObservation st = state.copy();

        int i;
        double acum = 0, avg;
        for (i = 0; i < SIMULATION_DEPTH; i++) {
            if (!st.isGameOver()) {
                ElapsedCpuTimer elapsedTimerIteration = new ElapsedCpuTimer();
                double[] out = client.calculate(extractNetworkInput(st));
                int index = 0;
                for(int j = 1; j < out.length; j++){
                    if(out[j] > out[index]){
                        index = j;
                    }
                }

                st.advance(action_mapping.get(index));

                acum += elapsedTimerIteration.elapsedMillis();
                avg = acum / (i + 1);
                remaining = timer.remainingTimeMillis();
                if (remaining < 2 * avg || remaining < BREAK_MS) break;
            } else {
                break;
            }
        }

        double winScore = 0;
        if(st.getGameWinner().equals(Types.WINNER.PLAYER_WINS)) {
            winScore = 1000;
        }
        if(st.getGameWinner().equals(Types.WINNER.PLAYER_LOSES)) {
            winScore = -1000;
        }
        client.setScore(heuristic.evaluateState(st) + winScore);// score + win condition
        numEvals++;
        acumTimeTakenEval += (elapsedTimerIterationEval.elapsedMillis());
        avgTimeTakenEval = acumTimeTakenEval / numEvals;
        remaining = timer.remainingTimeMillis();

        return client.getScore();
    }
}

