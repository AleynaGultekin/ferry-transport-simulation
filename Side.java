public enum Side {
    A,
    B;

    public Side opposite() {
        return this == A ? B : A;
    }
}
