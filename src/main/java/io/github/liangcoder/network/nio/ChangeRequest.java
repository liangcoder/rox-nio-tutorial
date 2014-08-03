package io.github.liangcoder.network.nio;

import java.nio.channels.SocketChannel;

public class ChangeRequest {

	public static final int REGISTER = 1;
	public static final int CHANGEEOPS = 2;
	
	public SocketChannel socket;
	public int type;
	public int ops;
	
	public ChangeRequest(SocketChannel socket, int type, int ops){
		this.socket = socket;
		this.type = type;
		this.ops = ops;
	}
}
