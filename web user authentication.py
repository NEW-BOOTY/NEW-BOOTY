# Import necessary libraries
import requests
from flask import Flask, jsonify, request, render_template
from itsdangerous import TimedJSONWebSignatureSerializer as Serializer
from functools import wraps

# Initialize Flask app
app = Flask(__name__)
app.config['SECRET_KEY'] = 'your-secret-key'

# Define the User model (replace with your actual user model)
class User:
    def __init__(self, id, username, role):
        self.id = id
        self.username = username
        self.role = role

# Mock database of users (replace with your actual user database)
users_db = {
    'admin': User(1, 'admin', 'admin'),
    'user': User(2, 'user', 'user')
}

# Generate a token for authenticated users
def generate_token(user_id, expiration=1800):
    s = Serializer(app.config['SECRET_KEY'], expiration)
    return s.dumps({'user_id': user_id}).decode('utf-8')

# Decorator to require token authentication
def token_required(f):
    @wraps(f)
    def decorated(*args, **kwargs):
        token = request.args.get('token')  # Get token from query string
        if not token:
            return jsonify({'message': 'Token is missing!'}), 403
        try:
            s = Serializer(app.config['SECRET_KEY'])
            data = s.loads(token)
        except:
            return jsonify({'message': 'Token is invalid or expired!'}), 403
        return f(*args, **kwargs)
    return decorated

# Decorator to require admin role
def admin_required(f):
    @wraps(f)
    def decorated(*args, **kwargs):
        token = request.args.get('token')
        s = Serializer(app.config['SECRET_KEY'])
        data = s.loads(token)
        user = users_db.get(data['user_id'])
        if user.role != 'admin':
            return jsonify({'message': 'Admin access required!'}), 401
        return f(*args, **kwargs)
    return decorated

# Route to authenticate user and return a token
@app.route('/login', methods=['POST'])
def login():
    username = request.json.get('username')
    password = request.json.get('password')
    user = users_db.get(username)
    if not user or password != 'password':  # Replace with actual password verification
        return jsonify({'message': 'Invalid credentials'}), 401
    token = generate_token(user.id)
    return jsonify({'token': token})

# Route to serve the HTML template
@app.route('/')
def index():
    return render_template('index.html')

# Route to perform an action that requires admin permissions
@app.route('/admin-action', methods=['GET'])
@token_required
@admin_required
def admin_action():
    # Perform admin-only action here
    return jsonify({'message': 'Admin action performed successfully'})

# Run the Flask app
if __name__ == '__main__':
    app.run(debug=True)

Copyright © 2024 Devin B. Royal. All Rights Reserved.


