package fmi.ethnowear.api.controller.archive.admin;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/auth")
public class AdminAuthController {

    @GetMapping
    public ResponseEntity<Void> verify() {
        return ResponseEntity.noContent().build();
    }
}
