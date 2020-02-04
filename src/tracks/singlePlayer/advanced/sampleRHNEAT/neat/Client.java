package tracks.singlePlayer.advanced.sampleRHNEAT.neat;

import tracks.singlePlayer.advanced.sampleRHNEAT.calculations.Calculator;
import tracks.singlePlayer.advanced.sampleRHNEAT.genome.Genome;

public class Client {
    private Calculator calculator;

    private Genome genome;
    private double score;

    public Genome breed(Client c1, Client c2) {
        if(c1.getScore() > c2.getScore()) return Genome.crossOver(c1.getGenome(), c2.getGenome());
        return Genome.crossOver(c2.getGenome(), c1.getGenome());
    }

    public void generate_calculator(){
        this.calculator = new Calculator(genome);
    }

    public double[] calculate(double... input){
        if(this.calculator == null) generate_calculator();
        return this.calculator.calculate(input);
    }

    public double distance(Client other) {
        return this.getGenome().distance(other.getGenome());
    }

    public void mutate() {
        genome.mutate();
    }

    public Calculator getCalculator() {
        return calculator;
    }
    public Genome getGenome() {
        return genome;
    }
    public void setGenome(Genome genome) {
        this.genome = genome;
    }
    public double getScore() {
        return score;
    }
    public void setScore(double score) {
        this.score = score;
    }

}
