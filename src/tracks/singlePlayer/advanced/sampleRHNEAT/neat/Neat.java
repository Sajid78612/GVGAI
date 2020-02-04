package tracks.singlePlayer.advanced.sampleRHNEAT.neat;

import tracks.singlePlayer.advanced.sampleRHNEAT.data_structures.RandomHashSet;
import tracks.singlePlayer.advanced.sampleRHNEAT.genome.ConnectionGene;
import tracks.singlePlayer.advanced.sampleRHNEAT.genome.Genome;
import tracks.singlePlayer.advanced.sampleRHNEAT.genome.NodeGene;

import java.util.HashMap;

public class Neat {
    public static final int MAX_NODES = (int)Math.pow(2,20); // 2 million nodes max

    private double C1 = 1, C2 = 1, C3 = 1;
    private double CP = 4;
    private double WEIGHT_SHIFT_STRENGTH = 0.3;
    private double WEIGHT_RANDOM_STRENGTH = 1;
    private double PROBABILITY_MUTATE_LINK = 0.01;
    private double PROBABILITY_MUTATE_NODE = 0.1;
    private double PROBABILITY_MUTATE_WEIGHT_SHIFT = 0.02;
    private double PROBABILITY_MUTATE_WEIGHT_RANDOM= 0.02;
    private double PROBABILITY_MUTATE_TOGGLE_LINK = 0;

    private HashMap<ConnectionGene, ConnectionGene> all_connections = new HashMap<>();
    private RandomHashSet<NodeGene> all_nodes = new RandomHashSet<>();
    private RandomHashSet<Client> clients = new RandomHashSet<>();

    private int max_clients;
    private int output_size;
    private int input_size;

    public Neat(int input_size, int output_size, int clients){
        this.reset(input_size, output_size, clients);
    }

    public Genome empty_genome(){
        Genome g = new Genome(this);
        for(int i = 0; i < input_size + output_size; i++){
            g.getNodes().add(getNode(i + 1));
        }
        return g;
    }
    public void reset(int input_size, int output_size, int clients){
        this.input_size = input_size;
        this.output_size = output_size;
        this.max_clients = clients;

        all_connections.clear();
        all_nodes.clear();
        this.clients.clear();

        for(int i = 0;i < input_size; i++){
            NodeGene n = getNode();
            n.setX(0.1);
            n.setY((i + 1) / (double)(input_size + 1));
        }

        for(int i = 0; i < output_size; i++){
            NodeGene n = getNode();
            n.setX(0.9);
            n.setY((i + 1) / (double)(output_size + 1));
        }

        for(int i = 0; i < max_clients; i++){
            Client c = new Client();
            c.setGenome(empty_genome());
            c.generate_calculator();
            this.clients.add(c);
        }
    }

    public Client getClient(int index) {
        return clients.get(index);
    }

    public static ConnectionGene getConnection(ConnectionGene con){
        ConnectionGene c = new ConnectionGene(con.getFrom(), con.getTo());
        c.setInnovation_number(con.getInnovation_number());
        c.setWeight(con.getWeight());
        c.setEnabled(con.isEnabled());
        return c;
    }
    public ConnectionGene getConnection(NodeGene node1, NodeGene node2){
        ConnectionGene connectionGene = new ConnectionGene(node1, node2);

        if(all_connections.containsKey(connectionGene)){
            connectionGene.setInnovation_number(all_connections.get(connectionGene).getInnovation_number());
        }else{
            connectionGene.setInnovation_number(all_connections.size() + 1);
            all_connections.put(connectionGene, connectionGene);
        }

        return connectionGene;
    }
    public void setReplaceIndex(NodeGene node1, NodeGene node2, int index){
        all_connections.get(new ConnectionGene(node1, node2)).setReplaceIndex(index);
    }
    public int getReplaceIndex(NodeGene node1, NodeGene node2){
        ConnectionGene con = new ConnectionGene(node1, node2);
        ConnectionGene data = all_connections.get(con);
        if(data == null) return 0;
        return data.getReplaceIndex();
    }

    public NodeGene getNode() {
        NodeGene n = new NodeGene(all_nodes.size() + 1);
        all_nodes.add(n);
        return n;
    }
    public NodeGene getNode(int id){
        if(id <= all_nodes.size()) {
            return all_nodes.get(id - 1);
        }
        return getNode();
    }

    public void evolve() {
        reproduce();
        mutate();
        for(Client c:clients.getData()){
            c.generate_calculator();
        }
    }

    private void reproduce() {
        for(Client c:clients.getData()){
            Client c1 = clients.random_element();
            Client c2 = clients.random_element();
            c.setGenome(c.breed(c1, c2));
            clients.add(c);
        }
    }

    public void mutate() {
        for(Client c:clients.getData()){
            c.mutate();
        }
    }

    public static void main(String[] args) {
        Neat neat = new Neat(10,1,1000);
        double[] in = new double[10];
        for(int i = 0; i < 10; i++) in[i] = Math.random();

        for(int i = 0; i < 100; i++){
            for(Client c:neat.clients.getData()){
                double score = c.calculate(in)[0];
                c.setScore(score);
            }
            neat.evolve();
            //neat.printSpecies();
        }

        for(Client c:neat.clients.getData()){
            for(ConnectionGene g:c.getGenome().getConnections().getData()){
                System.out.print(g.getInnovation_number()+ " ");
            }
            System.out.println();
        }

    }

    // Get best client
    public Client getBestClient() {
        int best = 0;

        for(int i = 0; i < this.clients.size(); ++i) {
            if (((Client)this.clients.get(i)).getScore() > ((Client)this.clients.get(best)).getScore()) {
                best = i;
            }
        }

        return (Client)this.clients.get(best);
    }
    // Getters
    public double getCP() {
        return CP;
    }
    public void setCP(double CP) {
        this.CP = CP;
    }
    public double getC1() {
        return C1;
    }
    public double getC2() {
        return C2;
    }
    public double getC3() {
        return C3;
    }
    public double getWEIGHT_SHIFT_STRENGTH() {
        return WEIGHT_SHIFT_STRENGTH;
    }
    public double getWEIGHT_RANDOM_STRENGTH() {
        return WEIGHT_RANDOM_STRENGTH;
    }
    public double getPROBABILITY_MUTATE_LINK() {
        return PROBABILITY_MUTATE_LINK;
    }
    public double getPROBABILITY_MUTATE_NODE() {
        return PROBABILITY_MUTATE_NODE;
    }
    public double getPROBABILITY_MUTATE_WEIGHT_SHIFT() {
        return PROBABILITY_MUTATE_WEIGHT_SHIFT;
    }
    public double getPROBABILITY_MUTATE_WEIGHT_RANDOM() {
        return PROBABILITY_MUTATE_WEIGHT_RANDOM;
    }
    public double getPROBABILITY_MUTATE_TOGGLE_LINK() {
        return PROBABILITY_MUTATE_TOGGLE_LINK;
    }
    public int getOutput_size() {
        return output_size;
    }
    public int getInput_size() {
        return input_size;
    }
}
