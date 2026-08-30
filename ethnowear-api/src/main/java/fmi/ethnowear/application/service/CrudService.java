package fmi.ethnowear.application.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

public interface CrudService<W, D> {

    Page<D> findAll(Pageable pageable);

    D findById(Long id);

    @Transactional
    D create(W input);

    @Transactional
    D update(Long id, W input);

    @Transactional
    void delete(Long id);
}
