#!/bin/bash

# Script to create a public GitHub repository and push the current folder to it.
# Prerequisites: GitHub CLI (gh) must be installed and authenticated ('gh auth login').

set -e

# Get current directory name as repo name
REPO_NAME=$(basename "$PWD")

# Check if gh CLI is installed
if ! command -v gh &> /dev/null; then
    echo "Error: GitHub CLI (gh) is not installed. Please install it first."
    exit 1
fi

# Check authentication status
echo "Checking GitHub authentication..."
if ! gh auth status &> /dev/null; then
    echo "Error: Not logged in to GitHub CLI. Please run 'gh auth login' first."
    exit 1
fi

# Initialize git if needed
if [ ! -d ".git" ]; then
    echo "Initializing git repository..."
    git init
    git branch -M main
else
    echo "Git repository already initialized."
fi

# Add files and commit if needed
if [ -n "$(git status --porcelain)" ]; then
    echo "Staging and committing changes..."
    git add .
    git commit -m "Initial commit: Repository setup"
else
    echo "Working tree clean or nothing to commit."
fi

# Check if remote 'origin' already exists
if git remote | grep -q "^origin$"; then
    echo "Remote 'origin' already exists."
    git push -u origin main
else
    echo "Creating public repository '$REPO_NAME' on GitHub..."
    # --public makes it public
    gh repo create "$REPO_NAME" --public --source=. --remote=origin --push
fi

echo "Success! Public repository is live."
