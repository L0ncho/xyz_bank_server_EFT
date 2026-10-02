package cl.duoc.xyzbank.coreservice.auth.infrastructure.rest;

import org.springframework.http.HttpMethod;
import org.springframework.util.AntPathMatcher;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The eight domain endpoints and the scope each one requires, matching channel-auth's
 * domain-endpoint table exactly. A plain, explicit table rather than annotations or AOP,
 * so it can be audited line-for-line against the spec.
 */
public final class DomainEndpointScopes {

    private record Route(HttpMethod method, String pattern, Set<String> scopes) {
    }

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private static final List<Route> ROUTES = List.of(
            new Route(HttpMethod.GET, "/internal/customers/*", Set.of("web:customers:read")),
            new Route(HttpMethod.GET, "/internal/customers/*/accounts", Set.of("web:accounts:read")),
            new Route(
                    HttpMethod.GET,
                    "/internal/accounts/*/balance",
                    Set.of("web:accounts:read", "mobile:accounts:read", "atm:read-balance", "interests:write")),
            new Route(
                    HttpMethod.GET,
                    "/internal/accounts/*/transactions",
                    Set.of("web:transactions:read", "mobile:transactions:read")),
            new Route(HttpMethod.GET, "/internal/accounts/*/interest-summary", Set.of("web:interests:read")),
            new Route(
                    HttpMethod.GET,
                    "/internal/transactions/*",
                    Set.of("web:transactions:read", "mobile:transactions:read")),
            new Route(HttpMethod.POST, "/internal/accounts/*/withdrawals", Set.of("atm:withdraw")),
            new Route(HttpMethod.POST, "/internal/accounts/*/interest-credits", Set.of("interests:write")),
            new Route(HttpMethod.POST, "/internal/accounts/*/interest-credit-reversals", Set.of("interests:write")));

    private DomainEndpointScopes() {
    }

    public static Optional<Set<String>> requiredScopesFor(String method, String path) {
        HttpMethod httpMethod = HttpMethod.valueOf(method);
        return ROUTES.stream()
                .filter(route -> route.method() == httpMethod && PATH_MATCHER.match(route.pattern(), path))
                .map(Route::scopes)
                .findFirst();
    }

    public static boolean isDomainEndpoint(String method, String path) {
        return requiredScopesFor(method, path).isPresent();
    }
}
