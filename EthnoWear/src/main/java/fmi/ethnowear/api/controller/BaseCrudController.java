package fmi.ethnowear.api.controller;

import fmi.ethnowear.api.dto.IdentifiableDto;
import fmi.ethnowear.application.service.CrudService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import static fmi.ethnowear.api.util.ResponseUtil.created;

public class BaseCrudController<W, D extends IdentifiableDto> {

    private final CrudService<W, D> service;

    protected BaseCrudController(CrudService<W, D> service) {
        this.service = service;
    }

    @GetMapping
    public Page<D> findAll(Pageable pageable) {
        return service.findAll(pageable);
    }

    @GetMapping("/{id}")
    public D findById(@PathVariable Long id) {
        return service.findById(id);
    }

    @PostMapping
    public ResponseEntity<D> create(@Valid @RequestBody W request) {
        D result = service.create(request);
        return created(result, result.id());
    }

    @PutMapping("/{id}")
    public D update(@PathVariable Long id, @Valid @RequestBody W request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
