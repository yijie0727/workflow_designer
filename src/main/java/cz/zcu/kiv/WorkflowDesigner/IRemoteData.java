package cz.zcu.kiv.WorkflowDesigner;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface IRemoteData  extends Remote {


    public BlockData getBlockData(String blockIdName) throws RemoteException;

    public void setBlockData(BlockData blockData, String blockIdName) throws RemoteException;


}
