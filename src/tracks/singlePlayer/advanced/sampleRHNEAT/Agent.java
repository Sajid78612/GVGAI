package tracks.singlePlayer.advanced.sampleRHNEAT;

import core.game.Observation;
import core.game.StateObservation;
import core.player.AbstractPlayer;
import ontology.Types;
import tools.ElapsedCpuTimer;
import tools.Vector2d;
import tracks.singlePlayer.advanced.sampleRHNEAT.neat.Client;
import tracks.singlePlayer.advanced.sampleRHNEAT.neat.Neat;
import tracks.singlePlayer.advanced.sampleRHNEAT.neat.Species;
import tracks.singlePlayer.tools.Heuristics.StateHeuristic;
import tracks.singlePlayer.tools.Heuristics.WinScoreHeuristic;
import tracks.singlePlayer.advanced.sampleRHNEAT.visual.Frame;

import java.awt.*;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;


public class Agent extends AbstractPlayer{

    // Parameters
    private int POPULATION_SIZE = 30;
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
    // variables
    private int SIMULATION_DEPTH = 15;
    private double percentageClientToKill = 0.2;
    private boolean firstTimeRan = true;
    private Neat neat;
    private int counter = 0;

    //Some game-related features
    private double max_distance; //maximum distance

    public Agent(StateObservation stateObs, ElapsedCpuTimer elapsedTimer) {
        heuristic = new WinScoreHeuristic(stateObs);
        this.timer = elapsedTimer;

        Dimension d = stateObs.getWorldDimension();
        max_distance = Math.sqrt(d.width*d.width + d.height*d.height);
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
        if(firstTimeRan) {
            neat = init_neat(stateObs);
            firstTimeRan = false;
        }
        else {
            neat.getSpecies().getData().sort(
                    new Comparator<Species>() {
                        @Override
                        public int compare(Species o1, Species o2) {
                            return Double.compare(o1.getScore(), o2.getScore());
                        }
                    });

            neat.getClients().getData().sort(
                    new Comparator<Client>() {
                        @Override
                        public int compare(Client o1, Client o2) {
                            return Double.compare(o1.getScore(), o2.getScore());
                        }
                    });

            double amount = percentageClientToKill * neat.getClients().size();
            for(int i = 0;i < amount; i++){
                neat.getClients().get(0).setSpecies(null);
                neat.getSpecies().remove(0);
            }
            //Randomly re-add the population lost
            /*
            for(int i = 0; i < amount; i++){
                Client c = new Client();
                c.setGenome(neat.empty_genome());
                c.generate_calculator();
                neat.getClients().add(c);
            }
             */

        }


        // RUN EVOLUTION
        remaining = timer.remainingTimeMillis();
        while (remaining > avgTimeTaken && remaining > BREAK_MS && keepIterating) {
            evolve(stateObs, neat);
            remaining = timer.remainingTimeMillis();
        }

        // RETURN ACTION, we have to return the best clients very first output
        double[] bestAction = neat.getBestClient().calculate(extractNetworkInput(stateObs));
        /* PRINT OUT GENOME AT TICK 400
        if(counter == 400) {
            new Frame(neat.getBestClient().getGenome());
        }
         */

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
        counter++;
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
//      INPUT_SIZE = 4;

        Neat neat = new Neat(INPUT_SIZE, N_ACTIONS, POPULATION_SIZE); //input nodes, output nodes, number of clients

        for (int i = 0; i < neat.getMax_clients(); i++) {
            if (i == 0 || remaining > avgTimeTakenEval && remaining > BREAK_MS) {
                rateClients(stateObs, neat.getClient(i), heuristic, i);
                remaining = timer.remainingTimeMillis();
            }
            else {
                break;
            }
        }
        return neat;
    }

    private void evolve(StateObservation stateObs, Neat neat) {
        for(int i = 0; i < neat.getMax_clients(); i++){
            rateClients(stateObs, neat.getClient(i), heuristic, i);
        }
        neat.evolve();
    }
    private double rateClients(StateObservation state, Client client, StateHeuristic heuristic, int individualIdx) {
        ElapsedCpuTimer elapsedTimerIterationEval = new ElapsedCpuTimer();
        StateObservation st = state.copy();

        double n = 0;
        int i;
        double acum = 0, avg;
        for (i = 0; i < SIMULATION_DEPTH; i++) {
            if (!st.isGameOver()) {
                ElapsedCpuTimer elapsedTimerIteration = new ElapsedCpuTimer();
                double[] out = client.calculate(extractNetworkInput(st)); //Calculate the outputs of NEAT for this client
                int index = 0;
                for(int j = 1; j < out.length; j++){
                    if(out[j] > out[index]){
                        index = j;
                    }
                }

                Types.ACTIONS action = action_mapping.get(index);
//                System.out.println("\t[" + individualIdx + "]-" + i + ": " + action);
                st.advance(action);

                n += heuristic.evaluateState(st);
                acum += elapsedTimerIteration.elapsedMillis();
                avg = acum / (i + 1);
                remaining = timer.remainingTimeMillis();
                if (remaining < 2 * avg || remaining < BREAK_MS) break;
            } else {
                break;
            }
        }

        client.setScore(n/SIMULATION_DEPTH);// score + win condition

//        System.out.println("Evolved individual. " + i + " steps, score: " + (n/SIMULATION_DEPTH));

        numEvals++;
        acumTimeTakenEval += (elapsedTimerIterationEval.elapsedMillis());
        avgTimeTakenEval = acumTimeTakenEval / numEvals;
        remaining = timer.remainingTimeMillis();

        return client.getScore();
    }


    ///INPUTS


    //methods to do tailored to asteroids only for now
    private double[] extractNetworkInput(StateObservation stateObs) {

        //Initialize some useful stuff.
        double feat[];
        ArrayList<Double> features = new ArrayList<>();
        Vector2d position = stateObs.getAvatarPosition();
        Vector2d orient = stateObs.getAvatarOrientation();
        Dimension d = stateObs.getWorldDimension();
        double hpRatio = 0.0;
        if(stateObs.getAvatarMaxHealthPoints() > 0)
            hpRatio = ((double) stateObs.getAvatarHealthPoints()) / stateObs.getAvatarMaxHealthPoints();


        /* SIMPLE FEATURES */

//        features.add(stateObs.getGameScore());      //Game score
//        features.add(stateObs.getAvatarSpeed());    //Avatar velocity
        features.add(position.x / d.width);     //Normalized x position
        features.add(position.y / d.height);    //Normalized y position
        features.add(orient.x);                 //X orientation
        features.add(orient.y);                 //Y Orientation
        features.add(hpRatio);                  //Percentage of hp left.


        /* RESOURCES OWNED */

        //We allow 3 input for resources, setting a theoretical maximum of 20.
        int MAX_RES = 3;
        double MAX_RES_VAL = 20.0;

        HashMap<Integer, Integer> resourcesOwned = stateObs.getAvatarResources();
        if(resourcesOwned != null)
        {
            int resCount = 0;
            for(Integer resourceId : resourcesOwned.keySet())
            {
                if(resCount == 3)
                    break;

                features.add(resourcesOwned.get(resourceId) / MAX_RES_VAL);
                resCount++;
            }

            //FILL UP TO MAX_RES
            int remainingRes = MAX_RES - resCount;
            if(remainingRes > 0) for(int i =0; i<remainingRes; ++i)
                features.add(0.0);
        }



        /* RELATION TO OTHER SPRITES (distance and orientation per category) */

        //NPCs
        feat = extractFeatures(stateObs.getNPCPositions(position), position, orient);
        features.add(feat[0]); features.add(feat[1]);

        //Immovable
        feat = extractFeatures(stateObs.getImmovablePositions(position), position, orient);
        features.add(feat[0]); features.add(feat[1]);

        //Resources
        feat = extractFeatures(stateObs.getResourcesPositions(position), position, orient);
        features.add(feat[0]); features.add(feat[1]);

        //Portals
        feat = extractFeatures(stateObs.getPortalsPositions(position), position, orient);
        features.add(feat[0]); features.add(feat[1]);

        //FromAvatar
        feat = extractFeatures(stateObs.getFromAvatarSpritesPositions(position), position, orient);
        features.add(feat[0]); features.add(feat[1]);


        //Turn into array and return.
        double[] out = new double[features.size()];
        for(int i  = 0; i < out.length; ++i)
            out[i] = features.get(i);

        return out;
    }

    private double[] extractFeatures(ArrayList<Observation>[] obs, Vector2d position, Vector2d orient)
    {
        double feat[] = new double[2];
        if(obs != null && obs.length > 0 && obs[0].size() > 0) {
            feat = featuresToSprite(position, orient, obs[0].get(0));
        }
        else
        {
            feat[0] = 1.0;
            feat[1] = 1.0;
        }
        return feat;
    }


    // Returns two values:
    //   [0]: normalized distance to spritePos;
    //   [1]: angle to spritePos (0: 0 degrees, 0.5: 90 degrees, -0.5 -90 degrees, -1: -180
    private double[] featuresToSprite(Vector2d avatarPos, Vector2d avatarDir, Observation sprite)
    {
        double feat[] = new double[2];

        //Direction vector
        Vector2d dir = new Vector2d(sprite.position.x - avatarPos.x, sprite.position.y - avatarPos.y);

        //Distance, normalized, with 1 being the value for maximal distance.
        feat[0] = 1 - (dir.mag() / max_distance);

        dir.normalise();
        double frontDotProduct = avatarDir.dot(dir);

        //avatar's right vector
        Vector2d avatarRight = new Vector2d(-avatarDir.y, avatarDir.x);
        double rightDotProduct = avatarRight.dot(dir);

        //normalize, from -1..1  to 1..0
        feat[1] = 1 - ((frontDotProduct+1) / 2.0);

        if(rightDotProduct < 0)
        {
            //target on avatar's left,
            feat[1] = feat[1] * (-1);
        }

//        System.out.println(avatarPos.x + " " + avatarPos.y  + " " + sprite.position.x  + " " + sprite.position.y  + " " + feat[0] + " " +
//                avatarDir.x + " " + avatarDir.y + " " + frontDotProduct + " " + rightDotProduct + " " + feat[1]);


        return feat;
    }


}

