package com.pulse.service;

import com.pulse.dto.CreateServiceRequest;
import com.pulse.entity.MonitoredService;
import com.pulse.dto.UpdateThresholdsRequest;
import com.pulse.exception.DuplicateServiceException;
import com.pulse.exception.ResourceNotFoundException;
import com.pulse.repository.ServiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServiceManagementServiceTest {
    @Mock
    private ServiceRepository repository;
    @Mock
    private EndpointDestinationValidator destinationValidator;

    private ServiceManagementService service;

    @BeforeEach
    void setUp() {
        service = new ServiceManagementService(repository, destinationValidator);
    }

    @Test
    void createsValidService() {
        when(repository.existsByNameIgnoreCase("Payment API")).thenReturn(false);
        when(repository.saveAndFlush(any(MonitoredService.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.create(new CreateServiceRequest(
                "  Payment API  ", "Payments", "https://api.example.com"
        ));

        assertThat(result.name()).isEqualTo("Payment API");
        assertThat(result.status().name()).isEqualTo("UNKNOWN");
        assertThat(result.id()).isNotNull();
        assertThat(result.cpuWarningThreshold()).isEqualByComparingTo("80.00");
        assertThat(result.memoryWarningThreshold()).isEqualByComparingTo("80.00");
        verify(repository).saveAndFlush(any(MonitoredService.class));
        verify(destinationValidator).validate("https://api.example.com");
    }

    @Test
    void rejectsBlankNameDefensively() {
        assertThatThrownBy(() -> service.create(new CreateServiceRequest("  ", null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Service name must not be blank");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void retrievesServicesInRepositoryOrder() {
        var first = new MonitoredService("Newest", null, null);
        var second = new MonitoredService("Older", null, null);
        when(repository.findAllByOrderByCreatedAtDescIdAsc()).thenReturn(List.of(first, second));

        assertThat(service.findAll()).extracting("name").containsExactly("Newest", "Older");
    }

    @Test
    void retrievesServiceById() {
        var entity = new MonitoredService("Payments", null, null);
        when(repository.findById(entity.getId())).thenReturn(Optional.of(entity));

        assertThat(service.findById(entity.getId()).name()).isEqualTo("Payments");
    }

    @Test
    void rejectsMissingServiceRetrieval() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deletesExistingService() {
        var entity = new MonitoredService("Payments", null, null);
        when(repository.findById(entity.getId())).thenReturn(Optional.of(entity));

        service.delete(entity.getId());

        verify(repository).delete(entity);
    }

    @Test
    void rejectsMissingServiceDeletion() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(id))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(repository, never()).delete(any());
    }

    @Test
    void rejectsDuplicateNameIgnoringCase() {
        when(repository.existsByNameIgnoreCase("payment api")).thenReturn(true);

        assertThatThrownBy(() -> service.create(new CreateServiceRequest("payment api", null, null)))
                .isInstanceOf(DuplicateServiceException.class);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void createsAndUpdatesCustomThresholds() {
        var entity = new MonitoredService("Payments", null, null,
                new BigDecimal("35.00"), new BigDecimal("45.00"));
        when(repository.findById(entity.getId())).thenReturn(Optional.of(entity));

        var result = service.updateThresholds(entity.getId(),
                new UpdateThresholdsRequest(new BigDecimal("55.555"), new BigDecimal("65")));

        assertThat(result.cpuWarningThreshold()).isEqualByComparingTo("55.56");
        assertThat(result.memoryWarningThreshold()).isEqualByComparingTo("65.00");
    }
}
