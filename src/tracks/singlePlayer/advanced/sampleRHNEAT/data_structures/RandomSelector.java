/*
Copyright (c) 2019 Luecx

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

This file can be found on https://github.com/Luecx/NEAT
 */

package data_structures;

import java.util.ArrayList;

public class RandomSelector<T> {

    private ArrayList<T> objects = new ArrayList<>();
    private ArrayList<Double> scores = new ArrayList<>();

    private double total_score = 0;

    public void add(T element, double score){
        objects.add(element);
        scores.add(score);
        total_score+=score;
    }

    public T random() {
        double v = Math.random() * total_score; //Range is from 0 to total score
        double c = 0;
        for(int i = 0; i < objects.size(); i++){
            c += scores.get(i);
            if(c >= v){
                return objects.get(i);
            }
        }
        return null;
    }

    public void reset() {
        objects.clear();
        scores.clear();
        total_score = 0;
    }

}
