# ILP Drone Delivery System - GraphQL API

## Overview
This project implements a drone delivery routing system with a GraphQL API for efficient data querying. It demonstrates overfetching prevention and optimal query capabilities for drone fleet management.

## Getting Started

### Prerequisites
- Docker installed on your system
- Port 8080 available

### Running the Application
Run this command to start the application up:
docker run -p 8080:8080 ilp_submission_image

### REST Endpoints
- Base URL: `http://localhost:8080/api/v1/`
- Health Check: `http://localhost:8080/actuator/health`

### GraphQL Endpoint
- URL: `http://localhost:8080/graphql`

## GraphQL Schema

For the complete schema definition see:
`src/main/resources/graphql/schema.graphqls`

# Testing the application

## For testing use Postman

## Example queries:

### Example 1
{
"query": "{ drones { id } }"
}

### Example 2
{
"query": "{ drone(id: 1) { id name currentServicePoint { name location { lng lat } } } }"
}

### Example 3
{
"query": "{ drones(filters: { capability: { cooling: true, minCapacity: 8.0 } }, orderBy: { field: COST_PER_MOVE, direction: ASC }, limit: 3) { id name capability { capacity cooling costPerMove } currentServicePoint { name location { lng lat } } estimatedCost(distance: 100) } }"
}
