package com.ecommerce.exception;

public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }

    public ResourceNotFoundException(String resource, Long id) {
        super(resource + " not found with id: " + id);
    }

    public ResourceNotFoundException(String resource, String field, String value){
        super(resource + "not found with " + field + ": " + value);
    }
    
    public ResourceNotFoundException(String resource, String field, Long value){
        super(resource + "not found with " + field + ": " + value);
    }
}
