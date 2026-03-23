## Getting Started for Windows Users

Before cloning the project, we need to configure the `.gitconfig` file.

1. Open a terminal or Command Prompt / Git Bash and execute: `git config --global -e`
2. You should see a file appear inside the prompt window:
   ```
   [user]
           name = user name
           email = useremail@gmail.com
   ```
3. Close the window and cancel any operation.

4. Navigate to the `.gitconfig` file and edit it:
    1. Go to directory `C:\Users\UserName`.
    2. Open the file `.gitconfig` in any text editor.
    3. Add the following section to the end of the file:
       ```
       [core]
               autocrlf = input
       ```
       The file should look like the following:
       ```
       [user]
               name = user name
               email = useremail@gmail.com
       [core]
               autocrlf = input
       ```
       NOTE: These steps should be done before cloning the project as the `core` element is essential.
       If the project has already been cloned, it should be deleted and cloned again with the `core` setting applied.

5. Install WSL2 and Ubuntu Terminal (you can follow this [tutorial](https://ubuntu.com/tutorials/install-ubuntu-on-wsl2-on-windows-10#2-install-wsl) or follow these steps):
    1. Open Windows PowerShell or Command Prompt (if you need permissions, then open as admin).
    2. Install WSL for the first time:
       ```
       wsl --install
       ```
    3. Run the following to check the version:
       ```
       wsl -l -v
       ```
    4. Set WSL to default to version 2:
       ```
       wsl --set-default-version 2
       ```
    5. Install Ubuntu:
       ```
       wsl --install -d Ubuntu
       ```
    6. Once Ubuntu is installed, your Command Prompt should look like this:
       ```
       computername@1234:/mnt/c/Users/UserName$
       ```
    7. To access WSL (Ubuntu), open Command Prompt and use:
       ```
       wsl
       ```
    8. Close Command Prompt.

6. Once WSL2 and Ubuntu Terminal are installed, open Command Prompt and run the following commands:
      ```
      wsl 
      sudo apt update
      sudo apt upgrade
      sudo apt install bpython
      bpython
      ```
   
7. Now we need to configure the JAVA_HOME variable:
   ```
   nano ~/.bashrc
   ```

8. The command above will open a file. Use the down arrow key and navigate to the last line of the file.

9. Add the following at the end of the file:
    ```
    JAVA_HOME=$(dirname $( readlink -f $(which java) ))
    JAVA_HOME=$(realpath "$JAVA_HOME"/../)
    export JAVA_HOME
    ```

10. Write out the file with the shortcut provided and then exit.
    ```
    sudo update-alternatives --config java
    sudo apt update
    ```

11. Use this command to check if Java 21 is installed:
    ```
    java -version
    ```

12. You should be presented with this:
    ```
    openjdk version "21.0.0"
    ```

13. To install unzip, run the following in the Ubuntu terminal:
    ```
    sudo apt-get install unzip
    ```

14. To install PostgreSQL in the Ubuntu terminal:
    ```
    sudo apt install postgresql postgresql-contrib
    ```

15. WSL needs to be enabled in Docker:
    1. Open Docker Desktop > Settings > Resources > WSL INTEGRATION.
    2. Tick the box where it says "Ubuntu" or the name of your Ubuntu terminal.

16. To run the project, use the `wsl` command in the IntelliJ terminal.

### Troubleshooting:

[Microsoft Docs for installing Linux on Windows](https://learn.microsoft.com/en-us/windows/wsl/install)

[Error code 0x80070520](https://www.majorgeeks.com/content/page/microsoft_store_error_0x80070520.html)

Update WSL version: `wsl --update --web-download`

[Windows Subsystem for Linux Documentation](https://learn.microsoft.com/en-us/windows/wsl/)

[Other WSL-related issues](https://learn.microsoft.com/en-us/windows/wsl/troubleshooting)
