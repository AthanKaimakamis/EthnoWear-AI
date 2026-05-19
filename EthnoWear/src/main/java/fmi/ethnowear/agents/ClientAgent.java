package fmi.ethnowear.agents;

import jade.core.Agent;

public class ClientAgent extends Agent {

    @Override
    protected void setup() {
        System.out.println(getLocalName() + " started");
    }
}
