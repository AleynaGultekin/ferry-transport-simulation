public class FerrySimulation {

    public static void main(String[] args) throws Exception {
        String configPath = args.length > 0 ? args[0] : "simulation.properties";
        SimulationConfig config = SimulationConfig.load(configPath);
        Simulation simulation = new Simulation(config);
        simulation.start();
    }
}
