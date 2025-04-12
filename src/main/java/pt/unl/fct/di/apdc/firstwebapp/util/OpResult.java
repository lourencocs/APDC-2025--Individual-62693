package pt.unl.fct.di.apdc.firstwebapp.util;

public class OpResult {
    private String operation;
    private Object Input;
    private String Token; // Or a class representing your token
    private Object Outor; // Can be a String or another JSON object

    public OpResult(String operation, Object Input, String Token, Object Outor) {
        this.operation = operation;
        this.Input = Input;
        this.Token = Token;
        this.Outor = Outor;
    }

    public String getOperation() {
        return operation;
    }

    public Object getInput() {
        return Input;
    }

    public String getToken() {
        return Token;
    }

    public Object getOutor() {
        return Outor;
    }

}
