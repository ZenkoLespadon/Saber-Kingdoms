package com.kingdomspvp.kingdoms.model;

public class Claim {
    private final int gridX;
    private final int gridZ;
    private String kingdomName = "None";
    private String factionName = "None";

    public Claim(int gridX, int gridZ) {
        this.gridX = gridX;
        this.gridZ = gridZ;
    }

    public int getGridX() {
        return gridX;
    }

    public int getGridZ() {
        return gridZ;
    }

    public String getKingdomName() {
        return kingdomName;
    }

    public void setKingdomName(String kingdomName) {
        this.kingdomName = kingdomName;
    }

    @Override
    public String toString() {
        return "Claim[" + gridX + "," + gridZ + ", kingdom=" + (kingdomName != null ? kingdomName : "None") + "]";
    }
}
