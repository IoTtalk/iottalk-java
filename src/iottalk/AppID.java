package iottalk;

import java.util.UUID;
import java.math.BigInteger;
import java.lang.NumberFormatException;

public class AppID{
    private UUID uuid;
    private boolean persistent_binding;
    
    public AppID(){
        uuid = UUID.randomUUID();
        persistent_binding = false;
    }
    
    public AppID(String uuidHexDigitString){
        persistent_binding = true;
        setUUID(uuidHexDigitString);
    }
    
    public AppID(String uuidHexDigitString, boolean _persistent_binding){
        persistent_binding = _persistent_binding;
        setUUID(uuidHexDigitString);
    }
    
    public boolean isPersistentBinding(){
        return persistent_binding;
    }
    
    public UUID getUUID(){
        return uuid;
    }
    
    public void setUUID(UUID _uuid){
        uuid = _uuid;
    }
    
    public void setUUID(String uuidHexDigitString){
        try{
            BigInteger uuidValue = new BigInteger(uuidHexDigitString, 16);
            BigInteger twoPow64 = new BigInteger("10000000000000000", 16);
            uuid = new UUID(uuidValue.divide(twoPow64).longValue(), uuidValue.remainder(twoPow64).longValue());
        }
        catch (NumberFormatException e){
            throw new NumberFormatException("Invalid device_addr. Input string should be hex string.");
        }
    }
    
    @Override
    public String toString() { 
        return uuid.toString(); 
    }
}