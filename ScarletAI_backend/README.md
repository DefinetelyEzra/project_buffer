# Project Setup Instructions

This guide will walk you through setting up the project on your local machine. Please follow the steps carefully to ensure all dependencies are installed correctly.

## Prerequisites
Before proceeding, ensure the following are installed on your system:

### Microsoft Visual C++ Build Tools
1. Download and install the **Microsoft Visual C++ Build Tools**.
2. During installation, ensure the following components are selected:
   - **Desktop development with C++**
   - **MSVC v143 - VS 2022 C++ x64/x86 build tools**
   - **Windows 11 SDK** (or the latest version available).

### Python
1. Ensure **Python 3.11** (or a compatible version) is installed. Download it from the [official Python website](https://www.python.org/downloads/).
2. Install required dependencies:
   ```sh
   pip install -r requirements.txt
   ```
   **Note:** If you encounter issues installing `spacy` with `pip`, proceed to **Option 2**.

## Option 2: Using Conda (Recommended for `spacy`)
### Install Miniconda or Anaconda
1. Download and install **Miniconda** or **Anaconda** from the official website.
2. Initialize Conda for PowerShell:
   ```sh
   conda init powershell
   ```
3. Restart PowerShell for the changes to take effect.

### Create a Conda Environment
1. Create a new Conda environment:
   ```sh
   conda create -n myenv
   ```
2. Activate the environment:
   ```sh
   conda activate myenv
   ```

### Install `spacy` Using Conda
1. Install `spacy` and its dependencies:
   ```sh
   conda install -c conda-forge spacy
   ```

### Install Remaining Dependencies
1. Install the rest of the dependencies using `pip`:
   ```sh
   pip install -r requirements.txt
   ```

## Step 3: Verify Installation
### Check Installed Packages
Run the following command to verify all packages are installed:
```sh
pip list  
or
conda list
```

### Test the Setup
Run your application or tests to ensure everything works as expected.

## Troubleshooting
### 1. C++ Build Tools Not Found
If you encounter errors related to missing **C++ Build Tools**, ensure they are installed correctly and added to your system PATH.

### 2. `spacy` Installation Issues
If `pip` fails to install `spacy`, use Conda as described in **Option 2**.

### 3. PowerShell Execution Policy
If Conda commands don’t work in PowerShell, set the execution policy to **RemoteSigned**:
```sh
Set-ExecutionPolicy RemoteSigned -Scope CurrentUser
```

### 4. Conflicting Dependencies
If you encounter version conflicts, use Conda to install compatible versions or manually adjust the versions in `requirements.txt`.

## Additional Notes
- Using **Conda** is recommended for managing dependencies, especially for packages like `spacy` that require compiled binaries.
- If you prefer using `pip`, ensure all build tools are installed and configured correctly.

---

## Installing the spaCy Language Model

The `en_core_web_sm` spaCy language model is required for this project.  It is *not* installed via `pip`.  Use the following command to download it:

```bash
python -m spacy download en_core_web_sm==3.8.0
or
python -m spacy download en_core_web_sm