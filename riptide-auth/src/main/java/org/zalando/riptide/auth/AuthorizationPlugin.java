package org.zalando.riptide.auth;

import org.zalando.riptide.Plugin;
import org.zalando.riptide.RequestArguments;
import org.zalando.riptide.RequestExecution;

import java.io.IOException;

import static com.google.common.net.HttpHeaders.AUTHORIZATION;

public final class AuthorizationPlugin implements Plugin {

    private final AuthorizationProvider provider;

    public AuthorizationPlugin(final AuthorizationProvider provider) {
        this.provider = provider;
    }

    @Override
    public RequestExecution aroundNetwork(final RequestExecution execution) {
        return arguments -> execution.execute(withAuthorizationIfAbsent(arguments));
    }

    private RequestArguments withAuthorizationIfAbsent(final RequestArguments arguments) throws IOException {
        if (arguments.getHeaders().containsKey(AUTHORIZATION)) {
            return arguments;
        }

        return arguments.withHeader(AUTHORIZATION, provider.get());
    }

}
