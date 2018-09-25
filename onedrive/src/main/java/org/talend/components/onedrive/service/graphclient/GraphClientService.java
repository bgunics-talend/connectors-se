package org.talend.components.onedrive.service.graphclient;

import com.microsoft.graph.authentication.IAuthenticationProvider;
import com.microsoft.graph.http.IHttpRequest;
import com.microsoft.graph.logger.ILogger;
import com.microsoft.graph.logger.LoggerLevel;
import com.microsoft.graph.models.extensions.IGraphServiceClient;
import com.microsoft.graph.requests.extensions.GraphServiceClient;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.talend.sdk.component.api.service.Service;

import javax.annotation.PostConstruct;

@Service
@Slf4j
public class GraphClientService {

    @Getter
    private IGraphServiceClient graphClient;

    @Setter
    private String accessToken;

    ILogger logger = new ILogger() {

        @Override
        public void setLoggingLevel(LoggerLevel loggerLevel) {

        }

        @Override
        public LoggerLevel getLoggingLevel() {
            return null;
        }

        @Override
        public void logDebug(String s) {

        }

        @Override
        public void logError(String s, Throwable throwable) {

        }
    };

    @PostConstruct
    public void init() {
        System.out.println("graphClient post construct");
        IAuthenticationProvider authenticationProvider = new IAuthenticationProvider() {

            @Override
            public void authenticateRequest(IHttpRequest request) {
                System.out.println("auth: " + accessToken);
                request.addHeader("Authorization", accessToken);
            }
        };

        graphClient = GraphServiceClient.builder().authenticationProvider(authenticationProvider).logger(logger).buildClient();
        // graphClient.getLogger().setLoggingLevel(LoggerLevel.ERROR);
    }
}
