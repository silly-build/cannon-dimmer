package com.cannonafkdim;

public enum CannonAfkDimMode
{
    FULL_UNTIL_ZERO_DIM("Mode 1: Full until zero dim"),
    GRADUAL_TO_PARTIAL("Mode 2: Gradual to partial");

    private final String name;

    CannonAfkDimMode(String name)
    {
        this.name = name;
    }

    @Override
    public String toString()
    {
        return name;
    }
}