package com.googlecode.greysanatomy.console.client;

import com.googlecode.greysanatomy.Configer;
import com.googlecode.greysanatomy.console.GreysAnatomyConsole;
import com.googlecode.greysanatomy.console.rmi.req.ReqHeart;
import com.googlecode.greysanatomy.console.server.ConsoleServerService;
import com.googlecode.greysanatomy.exception.ConsoleException;
import com.googlecode.greysanatomy.exception.PIDNotMatchException;

import java.io.IOException;
import java.rmi.Naming;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 控制台客户端
 *
 * @author chengtongda
 */
public class ConsoleClient {

    private static final Logger logger = Logger.getLogger("greysanatomy");

    private final ConsoleServerService consoleServer;
    private final long sessionId;

    private ConsoleClient(Configer configer) throws Exception {
        this.consoleServer = (ConsoleServerService) Naming.lookup(String.format("rmi://%s:%d/RMI_GREYS_ANATOMY",
                configer.getTargetIp(),
                configer.getTargetPort()));

        // 检查PID是否正确
        if (!consoleServer.checkPID(configer.getJavaPid())) {
            throw new PIDNotMatchException();
        }

        this.sessionId = this.consoleServer.register();
        new GreysAnatomyConsole(configer, sessionId).start(consoleServer);
//        new RISCGreysAnatomyConsole(configer, sessionId).start(consoleServer);
        heartBeat();
    }

    /**
     * 启动心跳侦测线程
     */
    private void heartBeat() {
        Thread heartBeatDaemon = new Thread("ga-console-client-heartbeat") {

            @Override
            public void run() {
                while (true) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        //
                    }
                    if (null == consoleServer) {
                        // 链接已关闭，客户端留着也没啥意思了，在这里退出JVM
                        if( logger.isLoggable(Level.INFO) ) {
                            logger.info("disconnect to ga-console-server, shutdown jvm.");
                        }
                        System.exit(0);
                        break;
                    } else {
                        boolean hearBeatResult = false;
                        try {
                            hearBeatResult = consoleServer.sessionHeartBeat(new ReqHeart(sessionId));
                        } catch (Exception e) {
                            //
                        }
                        //如果心跳失败，则说明超时了，那就gg吧
                        if (!hearBeatResult) {
                            if( logger.isLoggable(Level.INFO) ) {
                                logger.info("session time out to ga-console-server, shutdown jvm.");
                            }
                            System.exit(0);
                            break;
                        }
                    }
                }
            }

        };
        heartBeatDaemon.setDaemon(true);
        heartBeatDaemon.start();
    }

    private static volatile ConsoleClient instance;

    /**
     * 单例控制台客户端
     *
     * @param configer
     * @throws ConsoleException
     * @throws IOException
     */
    public static synchronized void getInstance(Configer configer) throws Exception {
        if (null == instance) {
            instance = new ConsoleClient(configer);
        }
    }

}
