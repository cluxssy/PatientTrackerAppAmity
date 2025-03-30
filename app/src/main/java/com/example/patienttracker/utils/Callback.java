package com.example.patienttracker.utils;

/**
 * Simple callback interface for asynchronous operations
 */
public interface Callback<T> {
    void accept(T result);
}