package dev.vedaaxis.api.rule;

import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AbilityCatalog {
    private final AbilityMapper mapper;

    public AbilityCatalog(AbilityMapper mapper) {
        this.mapper = mapper;
    }

    public Map<Long, AbilityDefinition> load() {
        return mapper.findAll().stream()
                .map(AbilityRow::toDefinition)
                .collect(Collectors.toUnmodifiableMap(AbilityDefinition::actionId, Function.identity()));
    }

    public Collection<AbilityDefinition> all() {
        return load().values();
    }

    public Optional<AbilityDefinition> find(long actionId) {
        return Optional.ofNullable(load().get(actionId));
    }
}
