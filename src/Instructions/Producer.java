package Instructions;

public abstract class Producer extends Consumer implements Predicateable {
    private final int destination;
    private int mappedDestination;
    private boolean remappedDestination;
    protected Integer predicate = null;
    Producer(int address, int destination, int consumed) {
        super(address, consumed);
        this.destination = destination;
        this.mappedDestination = destination;
        remappedDestination = false;
    }
    public int getDestination() {
        return destination;
    }
    public void setMappedDestination(int destination) {
        this.mappedDestination = destination;
        this.remappedDestination = true;
    }
    public boolean destinationIsRemapped() {
        return remappedDestination;
    }
    public int getMappedDestination() {
        return mappedDestination;
    }
    @Override
    public void setPredicate(int predicate) {
        this.predicate = predicate;
    }
    @Override
    public String getPredicateString() {
        return predicate == null ? "" : String.format("(p%d)", predicate);
    }
}
