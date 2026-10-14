# PriceNest - Full-stack House Price Prediction (Java)
Run: `./run.sh` (or `javac -d out src/*.java && java -cp out Main`) then open http://localhost:8080
Frontend: public/index.html | Backend: src/ (plain Java, no libraries) | Data: places.csv (470 states/cities/areas)
API: GET /api/locations, GET /api/model, GET /api/history, POST /api/predict
     {state,city,area,sqft,bedrooms,bathrooms,age}
Own data: data.csv (header: sqft,bedrooms,bathrooms,age,tier,price - tier 0/1/2, price in lakhs).
Saved estimates go to history.csv.
